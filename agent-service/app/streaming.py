from collections.abc import AsyncIterator
from typing import Any

from app.contracts import AgentCard
from app.graph.builder import build_graph


async def run_assistant(payload: dict[str, Any]) -> AsyncIterator[dict[str, Any]]:
    yield {"type": "task_started"}
    yield {"type": "node_started", "node": "route"}
    try:
        result = await build_graph().ainvoke({**payload, "events": []})
        for item in result.get("events", []):
            yield item
        card = AgentCard.model_validate(result["card"])
        yield {"type": "card_ready", "card": card.model_dump()}
        yield {"type": "completed"}
    except Exception:
        fallback = AgentCard(type="error", title="助手暂不可用", summary="本机 AI 服务暂未准备好，请稍后重试。")
        yield {"type": "card_ready", "card": fallback.model_dump()}
        yield {"type": "failed"}
