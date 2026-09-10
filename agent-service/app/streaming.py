from __future__ import annotations

import uuid
from collections.abc import AsyncIterator
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone

from app.auth import AuthPrincipal
from app.graph.builder import get_graph
from app.graph.context import RunContext
from app.graph.state import CandidatePlan, ProposedResult
from app.rag.retriever import KnowledgeUnavailable, RetrievalBundle, SharedKnowledgeRetriever
from app.restore import restore_session_state, revalidate_persistent_state
from app.run_store import FencedAsyncRedisSaver, RunStoreError, canonical_json, sha256_digest, utc_now
from app.tools.tourism_v3_client import TourismV3Client, TourismV3Error
from app.tools.tourism_v3_contracts import RunSummaryRequest
from app.v3_contracts import (
    ASSISTANT_CONTRACT_VERSION,
    EVENT_VERSION,
    TOURISM_CONTRACT_VERSION,
    AssistantCardV3,
    AssistantEventV3,
    CandidateMeta,
    CandidateRecord,
    CandidateRef,
    CancelRequestedData,
    CancelRequestedEvent,
    CancelRunRequest,
    CardReadyEvent,
    CompletedEvent,
    ErrorCard,
    FoodCandidatePayload,
    GenerateRequest,
    ItineraryCard,
    ItineraryData,
    KnowledgeAnswerCard,
    LastCommittedCard,
    PendingBookingCard,
    PendingBookingData,
    PersistentStateV3,
    ProgressData,
    ProgressEvent,
    PublicToolSummary,
    ServiceRecommendationCard,
    SolutionSnapshot,
    StayCandidatePayload,
    ToolSummaryData,
    ToolSummaryEvent,
)


@dataclass(frozen=True, slots=True)
class CancelDispatch:
    outcome: str
    event: dict[str, object] | None
    thread_id: str
    run_id: str
    fence_epoch: int


def _agent_type(request: GenerateRequest) -> str:
    if request.page_action is not None or request.selected_target is not None:
        return "SERVICE_RECOMMENDER"
    text = request.user_text.lower()
    if any(word in text for word in ("行程", "几天", "路线安排", "怎么玩", "慢游")):
        return "ITINERARY_PLANNER"
    if any(word in text for word in ("商品", "特产", "餐", "美食", "住", "民宿", "地点", "景点", "攻略")):
        return "SERVICE_RECOMMENDER"
    return "KNOWLEDGE_GUIDE"


def _plus_days(days: int) -> str:
    return (datetime.now(timezone.utc) + timedelta(days=days)).replace(microsecond=0).strftime("%Y-%m-%dT%H:%M:%SZ")


def _event_json(event: AssistantEventV3) -> dict[str, object]:
    return event.model_dump(mode="json", by_alias=True)


async def _persist_progress(
    store: FencedAsyncRedisSaver,
    thread_id: str,
    run_id: str,
    epoch: int,
    *,
    stage: str,
    status: str,
    completed: int,
    total: int | None,
) -> AssistantEventV3:
    sequence = await store.next_event_sequence(thread_id, run_id)
    event = AssistantEventV3(
        root=ProgressEvent(
            assistant_contract_version=ASSISTANT_CONTRACT_VERSION,
            tourism_contract_version=TOURISM_CONTRACT_VERSION,
            event_version=EVENT_VERSION,
            thread_id=thread_id,
            run_id=run_id,
            event_sequence=sequence,
            run_state="RUNNING",
            emitted_at=utc_now(),
            type="progress",
            data=ProgressData(
                stage=stage,
                status=status,
                completed_units=completed,
                total_units=total,
            ),
        )
    )
    await store.append_event(thread_id, run_id, epoch, event)
    return event


async def _persist_tool_summary(
    store: FencedAsyncRedisSaver,
    thread_id: str,
    run_id: str,
    epoch: int,
    summary: PublicToolSummary,
) -> AssistantEventV3:
    sequence = await store.next_event_sequence(thread_id, run_id)
    event = AssistantEventV3(
        root=ToolSummaryEvent(
            assistant_contract_version=ASSISTANT_CONTRACT_VERSION,
            tourism_contract_version=TOURISM_CONTRACT_VERSION,
            event_version=EVENT_VERSION,
            thread_id=thread_id,
            run_id=run_id,
            event_sequence=sequence,
            run_state="RUNNING",
            emitted_at=utc_now(),
            type="tool_summary",
            data=ToolSummaryData.model_validate(summary.model_dump(mode="python"), strict=True),
        )
    )
    await store.append_event(thread_id, run_id, epoch, event)
    return event


def _replace_verified_references(card: AssistantCardV3, bundle: RetrievalBundle) -> AssistantCardV3:
    if isinstance(card.root, ErrorCard) or card.root.type == "clarifying_question":
        return card
    payload = card.model_dump(mode="json", by_alias=True)
    payload["references"] = [item.model_dump(mode="json", by_alias=True) for item in bundle.references]
    if isinstance(card.root, KnowledgeAnswerCard):
        payload["data"]["demoData"] = bundle.demo_data
    return AssistantCardV3.model_validate(payload, strict=True)


def _candidate_record(
    request: GenerateRequest,
    principal: AuthPrincipal,
    plan: CandidatePlan,
    bundle: RetrievalBundle,
    logical_expires_at: str,
) -> CandidateRecord:
    created_at = utc_now()
    reference = CandidateRef(
        thread_id=request.thread_id,
        candidate_id=str(uuid.uuid4()),
        candidate_version=1,
    )
    action = "UPDATE" if request.base_resource is not None else "CREATE"
    material = {
        "candidateRef": reference.model_dump(mode="json", by_alias=True),
        "candidateType": plan.candidate_type,
        "adoptionAction": action,
        "baseResource": request.base_resource.model_dump(mode="json", by_alias=True) if request.base_resource else None,
        "sourceOwnerKind": principal.owner_kind,
        "createdAt": created_at,
        "expiresAt": logical_expires_at,
        "knowledgeContext": bundle.context.model_dump(mode="json", by_alias=True) if bundle.context else None,
        "knowledgeDependencies": [item.model_dump(mode="json", by_alias=True) for item in bundle.dependencies],
        "payload": plan.payload.model_dump(mode="json", by_alias=True),
    }
    return CandidateRecord.model_validate(
        {**material, "candidateDigest": sha256_digest(material)},
        strict=True,
    )


def _candidate_card(plan: CandidatePlan, record: CandidateRecord, bundle: RetrievalBundle) -> AssistantCardV3:
    meta = CandidateMeta(
        candidate_ref=record.candidate_ref,
        candidate_type=record.candidate_type,
        adoption_action=record.adoption_action,
        base_resource=record.base_resource,
        created_at=record.created_at,
        expires_at=record.expires_at,
    )
    references = bundle.references
    if plan.candidate_type == "ITINERARY":
        return AssistantCardV3(
            root=ItineraryCard(
                card_version="3.0",
                type="itinerary",
                title=plan.title[:80],
                summary=plan.summary[:800],
                references=references,
                actions=[
                    {"action": "SAVE_ITINERARY", "label": "保存个人行程", "candidateRef": record.candidate_ref}
                ],
                data=ItineraryData(
                    content=record.payload,
                    candidate=meta,
                    retrieval_mode=bundle.mode,
                    demo_data=plan.demo_data or bundle.demo_data,
                    notice="服务时间、价格和可预约情况请以确认页及服务方最终确认结果为准。",
                ),
            )
        )
    action = "ADOPT_FOOD_DRAFT" if plan.candidate_type == "FOOD_DRAFT" else "ADOPT_STAY_DRAFT"
    label = "保存为餐食草稿" if plan.candidate_type == "FOOD_DRAFT" else "保存为住宿草稿"
    return AssistantCardV3(
        root=PendingBookingCard(
            card_version="3.0",
            type="pending_booking",
            title=plan.title[:80],
            summary=plan.summary[:800],
            references=references,
            actions=[{"action": action, "label": label, "candidateRef": record.candidate_ref}],
            data=PendingBookingData(
                candidate=meta,
                proposal=record.payload,
                retrieval_mode=bundle.mode,
                demo_data=plan.demo_data or bundle.demo_data,
                notice="采用后只保存为草稿，仍需在确认页核价、补齐联系人并正式提交。",
            ),
        )
    )


async def run_assistant(
    request: GenerateRequest,
    principal: AuthPrincipal,
    store: FencedAsyncRedisSaver,
    run_id: str | None = None,
) -> AsyncIterator[dict[str, object]]:
    await store.require_thread_access(request.thread_id, principal.owner_kind, principal.owner_digest)
    if principal.owner_kind == "ANONYMOUS" and request.thread_id != principal.thread_id:
        raise RunStoreError("FORBIDDEN")
    snapshot = await store.session_snapshot(request.thread_id, principal.owner_kind, principal.owner_digest)
    if snapshot.checkpoint_status not in {"EMPTY", "AVAILABLE"}:
        raise RunStoreError(
            "CHECKPOINT_INCOMPATIBLE"
            if snapshot.checkpoint_status == "INCOMPATIBLE"
            else "CHECKPOINT_UNAVAILABLE"
        )
    prior_state, _, _, _ = await revalidate_persistent_state(snapshot, principal, store)
    run_id = run_id or str(uuid.uuid4())
    logical_expires_at = _plus_days(30)
    if principal.owner_kind == "ANONYMOUS":
        try:
            interaction = await TourismV3Client().accept_anonymous_interaction(
                request.thread_id,
                run_id,
                principal.anonymous_credential or "",
            )
            logical_expires_at = interaction.session_expires_at
        except TourismV3Error as exc:
            raise RunStoreError(exc.code) from exc
    agent_type = _agent_type(request)
    start = await store.start_run(
        thread_id=request.thread_id,
        run_id=run_id,
        client_request_id=request.client_request_id,
        request_digest=sha256_digest(request.model_dump(mode="json", by_alias=True)),
        owner_kind=principal.owner_kind,
        owner_digest=principal.owner_digest,
        logical_expires_at=logical_expires_at,
        expected_checkpoint_revision=request.expected_checkpoint_revision,
        agent_type=agent_type,
        retrieval_mode=request.retrieval_mode,
    )
    if start.replayed:
        state = await restore_session_state(request.thread_id, principal, store)
        yield state.model_dump(mode="json", by_alias=True)
        return
    if start.event is None:
        raise RunStoreError("RUN_STATUS_UNAVAILABLE")
    yield _event_json(start.event)
    yield _event_json(
        await _persist_progress(
            store,
            request.thread_id,
            run_id,
            start.fence_epoch,
            stage="UNDERSTANDING",
            status="COMPLETED",
            completed=1,
            total=1,
        )
    )
    yield _event_json(
        await _persist_progress(
            store,
            request.thread_id,
            run_id,
            start.fence_epoch,
            stage="RETRIEVING",
            status="STARTED",
            completed=0,
            total=None,
        )
    )
    graph_result = await get_graph(store.official_checkpointer).ainvoke(
        {},
        config=store.official_graph_config(request.thread_id, start.fence_epoch),
        context=RunContext(
            request=request,
            run_id=run_id,
            fence_epoch=start.fence_epoch,
            owner_kind=principal.owner_kind,
            logical_expires_at=logical_expires_at,
        ),
    )
    proposed: ProposedResult = graph_result["result"]
    for summary in proposed.tool_summaries:
        yield _event_json(
            await _persist_tool_summary(store, request.thread_id, run_id, start.fence_epoch, summary)
        )
    yield _event_json(
        await _persist_progress(
            store,
            request.thread_id,
            run_id,
            start.fence_epoch,
            stage="VERIFYING_KNOWLEDGE",
            status="STARTED" if proposed.retrieval.dependencies else "SKIPPED",
            completed=0,
            total=len(proposed.retrieval.dependencies) or None,
        )
    )
    bundle = proposed.retrieval
    try:
        bundle = await SharedKnowledgeRetriever().verify_for_delivery(bundle)
    except KnowledgeUnavailable as exc:
        from app.graph.nodes import error_card

        proposed = ProposedResult(
            intent=proposed.intent,
            card=error_card(exc.code),
            candidate_plan=None,
            retrieval=RetrievalBundle(mode="NONE", context=None, dependencies=[], evidence=[]),
            tool_summaries=proposed.tool_summaries,
        )
        bundle = proposed.retrieval
    yield _event_json(
        await _persist_progress(
            store,
            request.thread_id,
            run_id,
            start.fence_epoch,
            stage="PREPARING_RESULT",
            status="COMPLETED",
            completed=1,
            total=1,
        )
    )
    candidate = (
        _candidate_record(request, principal, proposed.candidate_plan, bundle, logical_expires_at)
        if proposed.candidate_plan
        else None
    )
    card = (
        _candidate_card(proposed.candidate_plan, candidate, bundle)
        if proposed.candidate_plan and candidate
        else _replace_verified_references(proposed.card, bundle)
    )
    if card is None:
        raise RunStoreError("FINAL_VALIDATION_FAILED")
    raw_prior_state = snapshot.checkpoint.state if snapshot.checkpoint is not None else None
    last_revision = (
        raw_prior_state.last_committed_card.card_revision
        if raw_prior_state and raw_prior_state.last_committed_card
        else 0
    )
    last_solution_revision = (
        raw_prior_state.current_solution.solution_revision
        if raw_prior_state and raw_prior_state.current_solution
        else 0
    )
    current_solution = prior_state.current_solution if prior_state else None
    previous_solution = prior_state.previous_solution if prior_state else None
    if candidate is not None:
        next_solution_revision = last_solution_revision + 1
        previous_solution = current_solution
        current_solution = SolutionSnapshot(
            solution_revision=next_solution_revision,
            card=card,
            candidate_ref=candidate.candidate_ref,
            candidate_type=candidate.candidate_type,
            adoption_action=candidate.adoption_action,
            base_resource=candidate.base_resource,
            created_at=candidate.created_at,
            expires_at=candidate.expires_at,
            knowledge_context=candidate.knowledge_context,
            knowledge_dependencies=candidate.knowledge_dependencies,
        )
    is_error = isinstance(card.root, ErrorCard)
    state = PersistentStateV3(
        intent=proposed.intent,
        confirmed_conditions=request.conditions,
        conversation_summary={
            "KNOWLEDGE": "本轮请求乌东资料讲解。",
            "SERVICE": "本轮请求乌东公开服务推荐。",
            "ITINERARY": "本轮请求乌东行程规划。",
            "FOOD_DRAFT": "本轮形成餐食待采用草稿方案。",
            "STAY_DRAFT": "本轮形成住宿待采用草稿方案。",
        }[proposed.intent],
        last_committed_card=LastCommittedCard(
            card_revision=last_revision + 1,
            card=card,
            knowledge_context=bundle.context,
            knowledge_dependencies=bundle.dependencies,
            committed_at=utc_now(),
        ),
        current_solution=current_solution,
        previous_solution=previous_solution,
        public_tool_summaries=proposed.tool_summaries,
        session_status="DEGRADED" if is_error else "WAITING_FOR_USER" if card.root.type == "clarifying_question" else "ACTIVE",
    )
    yield _event_json(
        await _persist_progress(
            store,
            request.thread_id,
            run_id,
            start.fence_epoch,
            stage="PERSISTING_RESULT",
            status="STARTED",
            completed=0,
            total=1,
        )
    )
    committed = await store.commit_card(
        thread_id=request.thread_id,
        run_id=run_id,
        epoch=start.fence_epoch,
        state=state,
        card=card,
        candidate=candidate,
        terminal_state="FAILED" if is_error else "COMPLETED",
        error_code=card.root.data.code if is_error else None,
    )
    for event in committed.events:
        yield _event_json(event)
    try:
        await TourismV3Client().write_run_summary(
            RunSummaryRequest(
                thread_id=request.thread_id,
                run_id=run_id,
                summary_sequence=2,
                run_state="FAILED" if is_error else "COMPLETED",
                agent_type=agent_type,
                retrieval_mode=bundle.mode,
                started_at=start.event.root.data.started_at,
                finished_at=committed.events[-1].root.data.finished_at,
                knowledge_dependencies=bundle.dependencies,
                candidate_refs=[candidate.candidate_ref.model_dump(mode="json", by_alias=True)] if candidate else [],
                error_code=card.root.data.code if is_error else None,
            )
        )
    except (TourismV3Error, ValueError):
        pass


async def request_cancel(
    request: CancelRunRequest,
    principal: AuthPrincipal,
    store: FencedAsyncRedisSaver,
) -> CancelDispatch:
    await store.require_thread_access(request.thread_id, principal.owner_kind, principal.owner_digest)
    if principal.owner_kind == "ANONYMOUS" and request.thread_id != principal.thread_id:
        raise RunStoreError("FORBIDDEN")
    epoch = await store.get_run_epoch(request.thread_id, request.run_id)
    while True:
        sequence = await store.next_event_sequence(request.thread_id, request.run_id)
        requested_at = utc_now()
        event = AssistantEventV3(
            root=CancelRequestedEvent(
                assistant_contract_version=ASSISTANT_CONTRACT_VERSION,
                tourism_contract_version=TOURISM_CONTRACT_VERSION,
                event_version=EVENT_VERSION,
                thread_id=request.thread_id,
                run_id=request.run_id,
                event_sequence=sequence,
                run_state="CANCEL_REQUESTED",
                emitted_at=requested_at,
                type="cancel_requested",
                data=CancelRequestedData(requested_at=requested_at, display_code="CANCEL_REQUESTED"),
            )
        )
        try:
            result = await store.request_cancel(
                thread_id=request.thread_id,
                run_id=request.run_id,
                epoch=epoch,
                cancel_request_id=request.cancel_request_id,
                event=event,
                owner_kind=principal.owner_kind,
                owner_digest=principal.owner_digest,
            )
            break
        except RunStoreError as exc:
            if exc.code != "EVENT_SEQUENCE_CONFLICT":
                raise
    return CancelDispatch(
        outcome=result.outcome,
        event=_event_json(result.event) if result.event is not None else None,
        thread_id=request.thread_id,
        run_id=request.run_id,
        fence_epoch=epoch,
    )
