from collections.abc import AsyncIterator
from typing import Any

from app.contracts import AgentCard
from app.graph.builder import get_graph


async def run_assistant(payload: dict[str, Any]) -> AsyncIterator[dict[str, Any]]:
    thread_id = str(payload.get("thread_id") or "demo-session")
    config = {"configurable": {"thread_id": thread_id}}
    graph_input = {
        **payload,
        "thread_id": thread_id,
        "messages": [{"role": "user", "content": str(payload.get("user_text") or "")}],
    }
    final_card: dict[str, Any] | None = None
    yield {"type": "task_started", "thread_id": thread_id}
    try:
        async for part in get_graph().astream(graph_input, config=config, stream_mode=["custom", "updates"], version="v2"):
            if part["type"] == "custom":
                yield part["data"]
            elif part["type"] == "updates":
                for update in part["data"].values():
                    if isinstance(update, dict) and update.get("card"):
                        final_card = update["card"]
        card = AgentCard.model_validate(final_card or {"type": "error", "title": "助手暂不可用"})
        yield {"type": "card_ready", "card": card.model_dump()}
        yield {"type": "failed" if card.type == "error" else "completed"}
    except Exception:
        fallback = AgentCard(type="error", title="助手暂不可用", summary="本机 AI 服务暂未准备好，请稍后重试。", data={"demoAvailable": True})
        yield {"type": "card_ready", "card": fallback.model_dump()}
        yield {"type": "failed"}
