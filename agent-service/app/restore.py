from __future__ import annotations

import hmac
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Literal

from app.auth import AuthPrincipal
from app.candidates import candidate_digest_material
from app.rag.retriever import KnowledgeUnavailable, RetrievalBundle, SharedKnowledgeRetriever
from app.run_store import FencedAsyncRedisSaver, RunStoreError, SessionSnapshot, sha256_digest
from app.v3_contracts import (
    ASSISTANT_CONTRACT_VERSION,
    STATE_VERSION,
    TOURISM_CONTRACT_VERSION,
    AssistantCardV3,
    AvailableSolutionSlot,
    CandidateMeta,
    CandidateRecord,
    EmptySolutionSlot,
    KnowledgeContext,
    KnowledgeDependency,
    LastCommittedCard,
    PersistentStateV3,
    PublicSolutionSlot,
    SessionState,
    SolutionSnapshot,
)


SlotStatus = Literal["AVAILABLE", "EMPTY", "EXPIRED", "VERSION_UNAVAILABLE", "LOOKUP_UNAVAILABLE", "INCOMPATIBLE"]


@dataclass(frozen=True, slots=True)
class CheckedSolution:
    original: SolutionSnapshot | None
    restored: SolutionSnapshot | None
    status: SlotStatus
    code: str | None


def _empty_slot(slot: Literal["CURRENT", "PREVIOUS"], status: SlotStatus, code: str | None) -> EmptySolutionSlot:
    return EmptySolutionSlot(slot=slot, status=status, solution=None, code=code)


def _candidate_meta(record: CandidateRecord) -> CandidateMeta:
    return CandidateMeta(
        candidate_ref=record.candidate_ref,
        candidate_type=record.candidate_type,
        adoption_action=record.adoption_action,
        base_resource=record.base_resource,
        created_at=record.created_at,
        expires_at=record.expires_at,
    )


def _candidate_card_parts(card: AssistantCardV3) -> tuple[CandidateMeta | None, object | None]:
    root = card.root
    if root.type == "itinerary":
        return root.data.candidate, root.data.content
    if root.type == "pending_booking":
        return root.data.candidate, root.data.proposal
    return None, None


def _replace_references(card: AssistantCardV3, bundle: RetrievalBundle) -> AssistantCardV3:
    payload = card.model_dump(mode="json", by_alias=True)
    payload["references"] = [item.model_dump(mode="json", by_alias=True) for item in bundle.references]
    if card.root.type == "knowledge_answer":
        payload["data"]["demoData"] = bundle.demo_data
    return AssistantCardV3.model_validate(payload, strict=True)


async def _verify_knowledge(
    card: AssistantCardV3,
    context: KnowledgeContext | None,
    dependencies: list[KnowledgeDependency],
) -> AssistantCardV3:
    if bool(dependencies) != (context is not None):
        raise RunStoreError("CHECKPOINT_INCOMPATIBLE")
    if not dependencies:
        retrieval_mode = getattr(card.root.data, "retrieval_mode", None)
        if card.root.references or retrieval_mode not in {None, "NONE"}:
            raise RunStoreError("CHECKPOINT_INCOMPATIBLE")
        return card
    if getattr(card.root.data, "retrieval_mode", None) != context.retrieval_mode:
        raise RunStoreError("CHECKPOINT_INCOMPATIBLE")
    try:
        bundle = await SharedKnowledgeRetriever().verify_for_delivery(
            RetrievalBundle(
                mode=context.retrieval_mode,
                context=context,
                dependencies=dependencies,
                evidence=[],
            )
        )
    except KnowledgeUnavailable as exc:
        raise RunStoreError(exc.code) from exc
    return _replace_references(card, bundle)


async def _check_solution(
    solution: SolutionSnapshot | None,
    thread_id: str,
    principal: AuthPrincipal,
    store: FencedAsyncRedisSaver,
) -> CheckedSolution:
    if solution is None:
        return CheckedSolution(None, None, "EMPTY", None)
    reference = solution.candidate_ref
    if reference.thread_id != thread_id:
        return CheckedSolution(solution, None, "INCOMPATIBLE", "CANDIDATE_PAYLOAD_INVALID")
    try:
        candidate = await store.get_candidate(
            reference.thread_id,
            reference.candidate_id,
            reference.candidate_version,
        )
    except RunStoreError as exc:
        if exc.code == "CANDIDATE_LOOKUP_UNAVAILABLE":
            return CheckedSolution(solution, None, "LOOKUP_UNAVAILABLE", exc.code)
        return CheckedSolution(solution, None, "INCOMPATIBLE", exc.code)
    if candidate is None:
        return CheckedSolution(solution, None, "VERSION_UNAVAILABLE", "CANDIDATE_VERSION_UNAVAILABLE")
    try:
        expires_at = datetime.strptime(candidate.expires_at, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc)
    except ValueError:
        return CheckedSolution(solution, None, "INCOMPATIBLE", "CANDIDATE_PAYLOAD_INVALID")
    if expires_at <= datetime.now(timezone.utc):
        return CheckedSolution(solution, None, "EXPIRED", "CANDIDATE_EXPIRED")
    card_meta, card_payload = _candidate_card_parts(solution.card)
    if (
        candidate.candidate_ref != reference
        or candidate.source_owner_kind != principal.owner_kind
        or candidate.candidate_type != solution.candidate_type
        or candidate.adoption_action != solution.adoption_action
        or candidate.base_resource != solution.base_resource
        or candidate.created_at != solution.created_at
        or candidate.expires_at != solution.expires_at
        or candidate.knowledge_context != solution.knowledge_context
        or candidate.knowledge_dependencies != solution.knowledge_dependencies
        or card_meta != _candidate_meta(candidate)
        or card_payload != candidate.payload
        or not hmac.compare_digest(candidate.candidate_digest, sha256_digest(candidate_digest_material(candidate)))
    ):
        return CheckedSolution(solution, None, "INCOMPATIBLE", "CANDIDATE_PAYLOAD_INVALID")
    try:
        card = await _verify_knowledge(
            solution.card,
            candidate.knowledge_context,
            candidate.knowledge_dependencies,
        )
    except RunStoreError as exc:
        status: SlotStatus = "INCOMPATIBLE" if exc.code == "KNOWLEDGE_UPDATED" else "LOOKUP_UNAVAILABLE"
        return CheckedSolution(solution, None, status, exc.code)
    restored = solution.model_copy(update={"card": card})
    return CheckedSolution(solution, restored, "AVAILABLE", None)


def _public_slot(slot: Literal["CURRENT", "PREVIOUS"], checked: CheckedSolution) -> PublicSolutionSlot:
    if checked.restored is None:
        return _empty_slot(slot, checked.status, checked.code)
    return AvailableSolutionSlot(slot=slot, status="AVAILABLE", solution=checked.restored.card, code=None)


async def _check_last_card(
    last: LastCommittedCard | None,
    current: CheckedSolution,
    previous: CheckedSolution,
) -> tuple[LastCommittedCard | None, Literal["EMPTY", "AVAILABLE", "KNOWLEDGE_UPDATED", "LOOKUP_UNAVAILABLE", "INCOMPATIBLE"]]:
    if last is None:
        return None, "EMPTY"
    card_meta, _ = _candidate_card_parts(last.card)
    if card_meta is not None:
        match = next(
            (
                item
                for item in (current, previous)
                if item.original is not None
                and item.original.candidate_ref == card_meta.candidate_ref
                and item.original.card == last.card
            ),
            None,
        )
        if match is None or match.restored is None:
            if match and match.code == "KNOWLEDGE_UPDATED":
                return None, "KNOWLEDGE_UPDATED"
            if match and match.status in {"EXPIRED", "VERSION_UNAVAILABLE", "LOOKUP_UNAVAILABLE"}:
                return None, "LOOKUP_UNAVAILABLE"
            return None, "INCOMPATIBLE"
        return last.model_copy(update={"card": match.restored.card}), "AVAILABLE"
    try:
        card = await _verify_knowledge(last.card, last.knowledge_context, last.knowledge_dependencies)
    except RunStoreError as exc:
        if exc.code == "KNOWLEDGE_UPDATED":
            return None, "KNOWLEDGE_UPDATED"
        if exc.code in {"KNOWLEDGE_ELIGIBILITY_UNAVAILABLE", "CHECKPOINT_UNAVAILABLE"}:
            return None, "LOOKUP_UNAVAILABLE"
        return None, "INCOMPATIBLE"
    return last.model_copy(update={"card": card}), "AVAILABLE"


async def revalidate_persistent_state(
    snapshot: SessionSnapshot,
    principal: AuthPrincipal,
    store: FencedAsyncRedisSaver,
) -> tuple[
    PersistentStateV3 | None,
    CheckedSolution,
    CheckedSolution,
    Literal["EMPTY", "AVAILABLE", "KNOWLEDGE_UPDATED", "LOOKUP_UNAVAILABLE", "INCOMPATIBLE"],
]:
    if snapshot.checkpoint is None:
        if snapshot.checkpoint_status == "EMPTY":
            empty = CheckedSolution(None, None, "EMPTY", None)
            return None, empty, empty, "EMPTY"
        slot_status: SlotStatus = (
            "LOOKUP_UNAVAILABLE" if snapshot.checkpoint_status == "LOOKUP_UNAVAILABLE" else "INCOMPATIBLE"
        )
        code = "CHECKPOINT_UNAVAILABLE" if slot_status == "LOOKUP_UNAVAILABLE" else "CHECKPOINT_INCOMPATIBLE"
        empty = CheckedSolution(None, None, slot_status, code)
        return None, empty, empty, snapshot.checkpoint_status
    state = snapshot.checkpoint.state
    current = await _check_solution(state.current_solution, snapshot.thread_id, principal, store)
    previous = await _check_solution(state.previous_solution, snapshot.thread_id, principal, store)
    last, last_status = await _check_last_card(state.last_committed_card, current, previous)
    restored = state.model_copy(
        update={
            "last_committed_card": last,
            "current_solution": current.restored,
            "previous_solution": previous.restored,
            "session_status": "DEGRADED" if last_status not in {"EMPTY", "AVAILABLE"} else state.session_status,
        }
    )
    return restored, current, previous, last_status


async def restore_session_state(
    thread_id: str,
    principal: AuthPrincipal,
    store: FencedAsyncRedisSaver,
) -> SessionState:
    snapshot = await store.session_snapshot(thread_id, principal.owner_kind, principal.owner_digest)
    restored, current, previous, last_status = await revalidate_persistent_state(snapshot, principal, store)
    return SessionState(
        assistant_contract_version=ASSISTANT_CONTRACT_VERSION,
        tourism_contract_version=TOURISM_CONTRACT_VERSION,
        state_version=STATE_VERSION,
        type="session_state",
        thread_id=thread_id,
        logical_expires_at=snapshot.logical_expires_at,
        checkpoint_revision=snapshot.checkpoint_revision,
        run=snapshot.run,
        can_start_new_run=snapshot.can_start_new_run,
        last_card_status=last_status,
        last_card=restored.last_committed_card.card if restored and restored.last_committed_card else None,
        current_solution=_public_slot("CURRENT", current),
        previous_solution=_public_slot("PREVIOUS", previous),
    )
