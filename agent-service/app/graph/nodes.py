from __future__ import annotations

from time import monotonic
from typing import Any, Literal

from app.graph.context import RunContext
from app.graph.state import CandidatePlan, ProposedResult, WorkflowState
from app.llm.deepseek_client import DeepSeekUnavailable, answer_from_evidence, compose_itinerary
from app.rag.retriever import KnowledgeUnavailable, RetrievalBundle, SharedKnowledgeRetriever
from app.tools.tourism_v3_client import TourismV3Client, TourismV3Error
from app.tools.tourism_v3_contracts import FoodItem, Place, Product, RoomType, RouteGuide, StayProperty
from app.v3_contracts import (
    AssistantCardV3,
    ClarifyingQuestionCard,
    ClarifyingQuestionData,
    ErrorCard,
    ErrorData,
    FoodCandidatePayload,
    ItineraryCard,
    ItineraryData,
    KnowledgeAnswerCard,
    KnowledgeAnswerData,
    PublicTarget,
    PublicToolSummary,
    RecommendationItem,
    ServiceRecommendationCard,
    ServiceRecommendationData,
    StayCandidatePayload,
)


def _empty_retrieval() -> RetrievalBundle:
    return RetrievalBundle(mode="NONE", context=None, dependencies=[], evidence=[])


def error_card(code: str, title: str = "助手暂不可用", *, retryable: bool = True) -> AssistantCardV3:
    return AssistantCardV3(
        root=ErrorCard(
            card_version="3.0",
            type="error",
            title=title,
            summary="当前无法安全提供该结果，请稍后由你主动重试。",
            references=[],
            actions=[{"action": "RETRY", "label": "重新尝试"}] if retryable else [],
            data=ErrorData(code=code, retryable=retryable),
        )
    )


def clarify_card(context: RunContext, fields: list[str], title: str = "请补充规划条件") -> AssistantCardV3:
    return AssistantCardV3(
        root=ClarifyingQuestionCard(
            card_version="3.0",
            type="clarifying_question",
            title=title,
            summary="补充这些公开规划条件后，我再继续生成。",
            references=[],
            actions=[],
            data=ClarifyingQuestionData(
                required_fields=fields,
                confirmed_conditions=context.request.conditions,
            ),
        )
    )


def select_agent(state: WorkflowState, runtime: Any) -> dict[str, object]:
    request = runtime.context.request
    if request.page_action in {
        "RECOMMEND_PRODUCT",
        "RECOMMEND_FOOD",
        "RECOMMEND_STAY",
        "BROWSE_PLACES",
        "BROWSE_ROUTE_GUIDES",
        "SHOW_MY_ORDERS",
    } or request.selected_target is not None:
        return {"agent_type": "SERVICE_RECOMMENDER"}
    text = request.user_text.lower()
    if any(word in text for word in ("行程", "几天", "路线安排", "怎么玩", "慢游")):
        return {"agent_type": "ITINERARY_PLANNER"}
    if any(word in text for word in ("商品", "特产", "餐", "美食", "住", "民宿", "地点", "景点", "攻略")):
        return {"agent_type": "SERVICE_RECOMMENDER"}
    return {"agent_type": "KNOWLEDGE_GUIDE"}


def route_selected_agent(state: WorkflowState) -> str:
    return state["agent_type"]


async def _retrieve(context: RunContext) -> RetrievalBundle:
    return await SharedKnowledgeRetriever().retrieve(
        context.request.user_text or context.request.page_action or "乌东",
        context.request.retrieval_mode,
    )


def _summary(category: Literal["CATALOG", "KNOWLEDGE"], operation: str, count: int, started: float) -> PublicToolSummary:
    return PublicToolSummary(
        tool_category=category,
        operation_code=operation,
        final_status="SUCCESS" if count else "EMPTY",
        item_count=count,
        duration_ms=max(0, int((monotonic() - started) * 1000)),
    )


async def knowledge_guide(state: WorkflowState, runtime: Any) -> dict[str, object]:
    context: RunContext = runtime.context
    started = monotonic()
    try:
        bundle = await _retrieve(context)
        if not bundle.evidence:
            card = error_card("KNOWLEDGE_NO_ELIGIBLE_SOURCE", "暂未找到可核验资料", retryable=False)
        else:
            answer = await answer_from_evidence(context.request.user_text, bundle.evidence)
            card = AssistantCardV3(
                root=KnowledgeAnswerCard(
                    card_version="3.0",
                    type="knowledge_answer",
                    title="乌东资料回答",
                    summary="根据当前生效资料整理。",
                    references=bundle.references,
                    actions=[],
                    data=KnowledgeAnswerData(
                        answer=answer,
                        retrieval_mode=bundle.mode,
                        demo_data=bundle.demo_data,
                    ),
                )
            )
        summaries = [_summary("KNOWLEDGE", "SEARCH_KNOWLEDGE", len(bundle.evidence), started)]
    except KnowledgeUnavailable as exc:
        bundle, summaries = _empty_retrieval(), []
        card = error_card(exc.code)
    except DeepSeekUnavailable:
        bundle, summaries = _empty_retrieval(), []
        card = error_card("MODEL_UNAVAILABLE")
    return {
        "result": ProposedResult(
            intent="KNOWLEDGE",
            card=card,
            candidate_plan=None,
            retrieval=bundle,
            tool_summaries=summaries,
        )
    }


def _target_type(context: RunContext) -> str:
    if context.request.selected_target:
        return context.request.selected_target.target_type
    mapping = {
        "RECOMMEND_PRODUCT": "PRODUCT",
        "RECOMMEND_FOOD": "FOOD",
        "RECOMMEND_STAY": "STAY",
        "BROWSE_PLACES": "PLACE",
        "BROWSE_ROUTE_GUIDES": "ROUTE_GUIDE",
    }
    if context.request.page_action in mapping:
        return mapping[context.request.page_action]
    text = context.request.user_text
    if any(word in text for word in ("餐", "美食", "吃")):
        return "FOOD"
    if any(word in text for word in ("住", "民宿", "房")):
        return "STAY"
    if any(word in text for word in ("商品", "特产", "买")):
        return "PRODUCT"
    if "攻略" in text:
        return "ROUTE_GUIDE"
    return "PLACE"


def _flatten_catalog(target_type: str, rows: list[object]) -> list[tuple[RecommendationItem, bool]]:
    result: list[tuple[RecommendationItem, bool]] = []
    for row in rows:
        if isinstance(row, StayProperty):
            for room in row.room_types:
                result.append(
                    (
                        RecommendationItem(
                            target_type="STAY",
                            target_id=room.id,
                            target_name=f"{row.name} · {room.name}",
                            summary=room.description,
                            reference_price=room.reference_price,
                            demo_price=room.demo_price,
                            tags=row.tags,
                            demo_data=row.demo_data or room.demo_data,
                        ),
                        False,
                    )
                )
        elif isinstance(row, Product):
            result.append(
                (
                    RecommendationItem(
                        target_type="PRODUCT",
                        target_id=row.id,
                        target_name=row.name,
                        summary=row.description,
                        reference_price=row.reference_price,
                        demo_price=row.demo_price,
                        tags=row.tags,
                        demo_data=row.demo_data,
                    ),
                    False,
                )
            )
        elif isinstance(row, FoodItem):
            result.append(
                (
                    RecommendationItem(
                        target_type="FOOD",
                        target_id=row.id,
                        target_name=row.name,
                        summary=row.description,
                        reference_price=row.reference_price,
                        demo_price=row.demo_price,
                        tags=row.tags,
                        demo_data=row.demo_data,
                    ),
                    False,
                )
            )
        elif isinstance(row, Place):
            result.append(
                (
                    RecommendationItem(
                        target_type="PLACE",
                        target_id=row.id,
                        target_name=row.name,
                        summary=row.description,
                        reference_price=None,
                        demo_price=None,
                        tags=row.tags,
                        demo_data=row.demo_data,
                    ),
                    row.schematic_position is not None,
                )
            )
        elif isinstance(row, RouteGuide):
            result.append(
                (
                    RecommendationItem(
                        target_type="ROUTE_GUIDE",
                        target_id=row.id,
                        target_name=row.title,
                        summary=row.route_summary,
                        reference_price=None,
                        demo_price=None,
                        tags=row.tags,
                        demo_data=row.demo_data,
                    ),
                    False,
                )
            )
    return result[:20]


async def _search_service_catalog(target_type: str, user_text: str) -> list[object]:
    """先按原句检索；自然语言未命中时退回该类公开目录的代表关键词。"""
    client = TourismV3Client()
    rows = list(await client.search_catalog(target_type, user_text or "乌东"))
    if rows:
        return rows
    fallback_keywords = {
        "FOOD": "苗家",
        "STAY": "民宿",
        "PRODUCT": "乌东",
        "PLACE": "寨",
        "ROUTE_GUIDE": "乌东",
    }
    fallback = fallback_keywords.get(target_type)
    return list(await client.search_catalog(target_type, fallback)) if fallback else []


def _candidate_plan(context: RunContext, target_type: str, rows: list[object]) -> CandidatePlan | AssistantCardV3 | None:
    selected = context.request.selected_target
    if selected is None or target_type not in {"FOOD", "STAY"}:
        return None
    if context.request.base_resource is not None:
        return error_card("TOOL_UNAVAILABLE", "暂不能安全更新已有草稿")
    conditions = context.request.conditions
    if target_type == "FOOD":
        merchant_items = conditions.merchant_items
        if merchant_items is None:
            return clarify_card(context, ["merchantItems"], "请确认同一商家的餐食条目")
        available = {item.id: item for item in rows if isinstance(item, FoodItem)}
        chosen = [available.get(item.food_item_id) for item in merchant_items.items]
        if not chosen or any(item is None or item.merchant_id != merchant_items.merchant_id for item in chosen):
            return error_card("CANDIDATE_PAYLOAD_INVALID", "所选餐食已不可用于方案", retryable=False)
        return CandidatePlan(
            candidate_type="FOOD_DRAFT",
            payload=FoodCandidatePayload(
                merchant_id=merchant_items.merchant_id,
                items=merchant_items.items,
                visit_at=conditions.visit_at,
                people_count=conditions.people_count,
            ),
            title="餐食待采用方案",
            summary="采用后只保存为餐食草稿，不会直接下单。",
            demo_data=any(item.demo_data for item in chosen if item is not None),
        )
    room = next(
        (
            room
            for stay in rows
            if isinstance(stay, StayProperty)
            for room in stay.room_types
            if room.id == selected.target_id
        ),
        None,
    )
    if room is None:
        return error_card("CANDIDATE_PAYLOAD_INVALID", "所选房型已不可用于方案", retryable=False)
    missing = [
        field
        for field, value in (
            ("checkInDate", conditions.check_in_date),
            ("checkOutDate", conditions.check_out_date),
            ("roomCount", conditions.room_count),
            ("peopleCount", conditions.people_count),
        )
        if value is None
    ]
    if missing:
        return clarify_card(context, missing, "请补住宿规划条件")
    return CandidatePlan(
        candidate_type="STAY_DRAFT",
        payload=StayCandidatePayload(
            room_type_id=room.id,
            check_in_date=conditions.check_in_date,
            check_out_date=conditions.check_out_date,
            room_count=conditions.room_count,
            people_count=conditions.people_count,
        ),
        title="住宿待采用方案",
        summary="采用后只保存为住宿草稿，不会直接预约。",
        demo_data=room.demo_data,
    )


async def service_recommender(state: WorkflowState, runtime: Any) -> dict[str, object]:
    context: RunContext = runtime.context
    if context.request.page_action == "SHOW_MY_ORDERS":
        result = ProposedResult(
            intent="SERVICE",
            card=error_card("TOOL_UNAVAILABLE", "订单读取暂未接入", retryable=False),
            candidate_plan=None,
            retrieval=_empty_retrieval(),
            tool_summaries=[],
        )
        return {"result": result}
    target_type = _target_type(context)
    started = monotonic()
    try:
        rows = await _search_service_catalog(target_type, context.request.user_text)
        bundle = await _retrieve(context)
        summary = _summary("CATALOG", "SEARCH_CATALOG", len(rows), started)
        planned = _candidate_plan(context, target_type, rows)
        if isinstance(planned, AssistantCardV3):
            card, candidate = planned, None
        elif isinstance(planned, CandidatePlan):
            card, candidate = None, planned
        else:
            flattened = _flatten_catalog(target_type, rows)
            if not flattened:
                card, candidate = error_card("TOOL_UNAVAILABLE", "未找到当前公开目录内容", retryable=False), None
            else:
                actions: list[dict[str, object]] = []
                for item, drawable in flattened[:8]:
                    if item.target_type == "ROUTE_GUIDE":
                        actions.append(
                            {"action": "OPEN_ROUTE_GUIDE", "label": "查看攻略", "targetType": "ROUTE_GUIDE", "targetId": item.target_id}
                        )
                    else:
                        actions.append(
                            {"action": "OPEN_DETAIL", "label": "查看详情", "targetType": item.target_type, "targetId": item.target_id}
                        )
                        if item.target_type == "PLACE" and drawable:
                            actions.append(
                                {"action": "OPEN_MAP", "label": "查看水彩示意", "targetType": "PLACE", "targetId": item.target_id}
                            )
                card = AssistantCardV3(
                    root=ServiceRecommendationCard(
                        card_version="3.0",
                        type="service_recommendation",
                        title="乌东公开服务推荐",
                        summary="以下内容来自当前公开目录；演示价格不代表正式报价。",
                        references=bundle.references,
                        actions=actions[:20],
                        data=ServiceRecommendationData(
                            items=[item for item, _ in flattened],
                            retrieval_mode=bundle.mode,
                            notice="目录价格仅供展示，正式提交前由 Java 重新核价。",
                        ),
                    )
                )
                candidate = None
        intent = "FOOD_DRAFT" if candidate and candidate.candidate_type == "FOOD_DRAFT" else "STAY_DRAFT" if candidate else "SERVICE"
        result = ProposedResult(
            intent=intent,
            card=card,
            candidate_plan=candidate,
            retrieval=bundle,
            tool_summaries=[summary],
        )
    except KnowledgeUnavailable as exc:
        result = ProposedResult(
            intent="SERVICE",
            card=error_card(exc.code),
            candidate_plan=None,
            retrieval=_empty_retrieval(),
            tool_summaries=[],
        )
    except TourismV3Error as exc:
        result = ProposedResult(
            intent="SERVICE",
            card=error_card(exc.code),
            candidate_plan=None,
            retrieval=_empty_retrieval(),
            tool_summaries=[],
        )
    return {"result": result}


async def itinerary_planner(state: WorkflowState, runtime: Any) -> dict[str, object]:
    context: RunContext = runtime.context
    started = monotonic()
    try:
        rows = list(await TourismV3Client().search_catalog("PLACE", context.request.user_text or "乌东"))
        if not rows:
            rows = list(await TourismV3Client().search_catalog("PLACE", "演示"))
        targets = [
            PublicTarget(target_type="PLACE", target_id=item.id, target_name=item.name)
            for item in rows
            if isinstance(item, Place)
        ]
        bundle = await _retrieve(context)
        content = await compose_itinerary(
            context.request.user_text,
            context.request.conditions,
            targets,
            bundle.evidence,
        )
        plan = CandidatePlan(
            candidate_type="ITINERARY",
            payload=content,
            title=content.title,
            summary="这是待采用的行程方案；采用后只保存为个人行程。",
            demo_data=bundle.demo_data or any(item.demo_data for item in rows if isinstance(item, Place)),
        )
        result = ProposedResult(
            intent="ITINERARY",
            card=None,
            candidate_plan=plan,
            retrieval=bundle,
            tool_summaries=[_summary("CATALOG", "SEARCH_CATALOG", len(rows), started)],
        )
    except KnowledgeUnavailable as exc:
        result = ProposedResult(
            intent="ITINERARY",
            card=error_card(exc.code),
            candidate_plan=None,
            retrieval=_empty_retrieval(),
            tool_summaries=[],
        )
    except TourismV3Error as exc:
        result = ProposedResult(
            intent="ITINERARY",
            card=error_card(exc.code),
            candidate_plan=None,
            retrieval=_empty_retrieval(),
            tool_summaries=[],
        )
    except DeepSeekUnavailable:
        result = ProposedResult(
            intent="ITINERARY",
            card=error_card("MODEL_UNAVAILABLE"),
            candidate_plan=None,
            retrieval=_empty_retrieval(),
            tool_summaries=[],
        )
    return {"result": result}
