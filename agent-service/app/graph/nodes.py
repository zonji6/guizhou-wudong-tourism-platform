from typing import Any

from langgraph.config import get_stream_writer

from app.contracts import AgentCard, Source
from app.llm.deepseek_client import DeepSeekUnavailable, compose_itinerary
from app.rag.retriever import KnowledgeRetriever
from app.tools.tourism_client import TourismClient, service_card_item


def write_event(type_: str, **payload: Any) -> None:
    get_stream_writer()({"type": type_, **payload})


def route_request(state: dict[str, Any]) -> str:
    return "fast" if state.get("page_action") else "slow"


async def fast_lane(state: dict[str, Any]) -> dict[str, Any]:
    action = state.get("page_action") or "recommend"
    keyword_map = {"recommend_stay": "民宿", "recommend_food": "长桌宴", "recommend_culture": "茶旅", "recommend_travel": "接驳"}
    try:
        write_event("node_started", node="service_search", message="正在匹配平台服务")
        services = await TourismClient().search_services(keyword_map.get(action, ""))
        write_event("tool_finished", tool="service_search", service_count=len(services))
        card = AgentCard(type="service_recommendation", title="为你推荐贵州乌东体验", summary="以下为平台服务，请进入详情核对演示数据与预约信息。", data={"services": [service_card_item(item) for item in services[:4]]}) if services else AgentCard(type="error", title="暂未找到匹配服务", summary="可以先浏览游乌东资源列表。")
    except Exception:
        card = AgentCard(type="error", title="服务暂不可用", summary="资源服务尚未启动，请稍后再试。")
    return {"card": card.model_dump(), "messages": [{"role": "assistant", "content": card.summary}]}


async def identify_intent(state: dict[str, Any]) -> dict[str, Any]:
    write_event("node_started", node="intent", message="正在理解你的出行需求")
    text = state.get("user_text", "")
    intent = "booking" if any(word in text for word in ("预约", "预订", "订")) else "knowledge" if any(word in text for word in ("介绍", "历史", "资料", "知识")) else "itinerary"
    return {"intent": intent}


def decide_intent(state: dict[str, Any]) -> str:
    return "clarify" if not state.get("user_text") else state.get("intent", "itinerary")


async def clarify(state: dict[str, Any]) -> dict[str, Any]:
    card = AgentCard(type="clarifying_question", title="让我为你安排贵州乌东之行", summary="请告诉我出行日期、人数，以及更偏好茶旅、苗寨文化、美食还是民宿。")
    return {"card": card.model_dump(), "messages": [{"role": "assistant", "content": card.summary}]}


async def retrieve(state: dict[str, Any]) -> dict[str, Any]:
    write_event("node_started", node="retrieve", message="正在查找乌东资料")
    chunks = await KnowledgeRetriever().retrieve(state.get("user_text", ""))
    write_event("tool_finished", tool="knowledge_retrieval", source_count=len(chunks))
    return {"retrieved_chunks": chunks}


async def itinerary(state: dict[str, Any]) -> dict[str, Any]:
    chunks = state.get("retrieved_chunks", [])
    try:
        write_event("node_started", node="service_search", message="正在匹配平台服务")
        services = await TourismClient().search_services()
        write_event("tool_finished", tool="service_search", service_count=len(services))
        write_event("node_started", node="itinerary", message="正在生成行程建议")
        generated = await compose_itinerary(str(state.get("user_text") or ""), state.get("messages", []), services, chunks)
        card = AgentCard(type="itinerary", title=generated["title"], summary=generated["summary"], data=generated["data"], sources=[Source(title=item["title"], document_id=str(item["document_id"])) for item in chunks])
    except DeepSeekUnavailable:
        card = AgentCard(type="error", title="暂时无法生成行程", summary="模型当前不可用，你可以稍后重试或主动查看演示结果。", data={"demoAvailable": True})
    except Exception:
        card = AgentCard(type="error", title="暂时无法生成行程", summary="服务当前不可用，请稍后重试。", data={"demoAvailable": True})
    return {"card": card.model_dump(), "messages": [{"role": "assistant", "content": card.summary}]}


async def knowledge_answer(state: dict[str, Any]) -> dict[str, Any]:
    chunks = state.get("retrieved_chunks", [])
    if not chunks:
        card = AgentCard(type="error", title="当前知识库未收录相关资料", summary="请在后台补充当地文旅资料后再提问。")
    else:
        mode = next((item.get("retrieval_mode") for item in chunks if item.get("retrieval_mode")), None)
        card = AgentCard(type="knowledge_answer", title="贵州乌东文化资料", summary=chunks[0]["content"], data={"retrievalMode": mode} if mode else {}, sources=[Source(title=item["title"], document_id=str(item["document_id"])) for item in chunks])
    return {"card": card.model_dump(), "messages": [{"role": "assistant", "content": card.summary}]}


async def pending_booking(state: dict[str, Any]) -> dict[str, Any]:
    required = (state.get("service_id"), state.get("travel_date"), state.get("people"), state.get("contact_name"), state.get("contact_phone"))
    if not all(required):
        card = AgentCard(type="clarifying_question", title="请先补全预约信息", summary="请在预约确认页核对服务、日期、人数和联系人，再提交待确认预约。", data={"serviceId": state["service_id"]} if state.get("service_id") else {})
    else:
        try:
            client = TourismClient(); write_event("node_started", node="service_search", message="正在核对预约服务")
            services = await client.search_services(); selected = next((item for item in services if str(item["id"]) == str(state["service_id"])), None)
            write_event("tool_finished", tool="service_search", service_count=len(services))
            if selected is None: raise ValueError("预约服务不存在")
            booking = await client.create_pending_booking(str(state["service_id"]), str(state["travel_date"]), int(state["people"]), str(state["contact_name"]), str(state["contact_phone"]), str(state.get("thread_id") or "demo-session"))
            safe = {key: booking.get(key) for key in ("id", "serviceId", "serviceName", "travelDate", "peopleCount", "status")}; safe["demoData"] = selected.get("demoData", True)
            card = AgentCard(type="pending_booking", title="待确认预约", summary="请在页面再次确认后生成正式订单。", data={"booking": safe})
        except Exception:
            card = AgentCard(type="error", title="暂时无法创建预约", summary="预约服务暂不可用，请稍后再试。")
    return {"card": card.model_dump(), "messages": [{"role": "assistant", "content": card.summary}]}
