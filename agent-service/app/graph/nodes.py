from typing import Any

from app.contracts import AgentCard
from app.rag.retriever import KnowledgeRetriever
from app.tools.tourism_client import TourismClient


def event(state: dict[str, Any], type_: str, **payload: Any) -> list[dict[str, Any]]:
    return [*state.get("events", []), {"type": type_, **payload}]


def route_request(state: dict[str, Any]) -> str:
    return "fast" if state.get("page_action") else "slow"


async def fast_lane(state: dict[str, Any]) -> dict[str, Any]:
    action = state.get("page_action") or "recommend"
    keyword_map = {
        "recommend_stay": "民宿",
        "recommend_food": "酸汤鱼",
        "recommend_culture": "苗族 茶旅",
        "recommend_travel": "两日游",
    }
    try:
        services = await TourismClient().search_services(keyword_map.get(action, ""))
        card = AgentCard(
            type="service_recommendation",
            title="为你推荐贵州乌冬体验",
            summary="以下为平台演示资源，可进入详情页进一步预约。",
            data={"services": services[:4]},
        )
    except Exception:
        card = AgentCard(type="error", title="服务暂不可用", summary="资源服务尚未启动，请稍后再试。")
    return {"card": card.model_dump(), "events": event(state, "card_ready")}


async def identify_intent(state: dict[str, Any]) -> dict[str, Any]:
    text = state.get("user_text", "")
    if any(word in text for word in ("预约", "预订", "订")):
        intent = "booking"
    elif any(word in text for word in ("介绍", "历史", "资料", "知识")):
        intent = "knowledge"
    else:
        intent = "itinerary"
    return {"intent": intent, "events": event(state, "node_started", node="intent")}


def decide_intent(state: dict[str, Any]) -> str:
    if not state.get("user_text"):
        return "clarify"
    return state.get("intent", "itinerary")


async def clarify(state: dict[str, Any]) -> dict[str, Any]:
    card = AgentCard(
        type="clarifying_question",
        title="让我为你安排贵州乌冬之行",
        summary="请告诉我出行日期、人数，以及更偏好茶旅、苗寨文化、美食还是民宿。",
    )
    return {"card": card.model_dump(), "events": event(state, "card_ready")}


async def retrieve(state: dict[str, Any]) -> dict[str, Any]:
    chunks = await KnowledgeRetriever().retrieve(state.get("user_text", ""))
    return {"retrieved_chunks": chunks, "events": event(state, "tool_finished", tool="knowledge_retrieval")}


async def itinerary(state: dict[str, Any]) -> dict[str, Any]:
    card = AgentCard(
        type="itinerary",
        title="贵州乌冬苗族文化茶旅 · 两日建议",
        summary="以苗寨自然风光、茶园体验和本地风味为主的演示行程。",
        data={
            "days": [
                {"day": 1, "items": ["苗岭茶园采茶", "手工制茶与品茗", "苗寨长桌宴"]},
                {"day": 2, "items": ["苗族文化漫游", "香包手作体验", "酸汤鱼午餐"]},
            ],
            "notice": "服务时间与价格为演示数据，请以现场为准。",
        },
        sources=[{"title": item.get("title", "贵州乌冬知识库"), "document_id": item.get("document_id")} for item in state.get("retrieved_chunks", [])],
    )
    return {"card": card.model_dump(), "events": event(state, "card_ready")}


async def knowledge_answer(state: dict[str, Any]) -> dict[str, Any]:
    chunks = state.get("retrieved_chunks", [])
    if not chunks:
        card = AgentCard(type="error", title="当前知识库未收录相关资料", summary="请在后台补充当地文旅资料后再提问。")
    else:
        card = AgentCard(
            type="knowledge_answer",
            title="贵州乌冬文化资料",
            summary=chunks[0]["content"],
            sources=[{"title": item["title"], "document_id": item["document_id"]} for item in chunks],
        )
    return {"card": card.model_dump(), "events": event(state, "card_ready")}


async def pending_booking(state: dict[str, Any]) -> dict[str, Any]:
    service_id = state.get("service_id")
    if not service_id:
        card = AgentCard(type="clarifying_question", title="请选择要预约的体验", summary="选择具体服务后，我会为你生成待确认预约单。")
    else:
        try:
            booking = await TourismClient().create_pending_booking(
                service_id,
                state.get("travel_date"),
                state.get("people"),
                state.get("thread_id", "demo-session"),
            )
            card = AgentCard(type="pending_booking", title="待确认预约", summary="请在页面确认后才会生成正式订单。", data={"booking": booking})
        except Exception:
            card = AgentCard(type="error", title="暂时无法创建预约", summary="订单服务尚未启动，请稍后再试。")
    return {"card": card.model_dump(), "events": event(state, "card_ready")}
