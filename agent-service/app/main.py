from typing import Any

from fastapi import FastAPI, WebSocket, WebSocketDisconnect

from app.config import get_settings
from app.contracts import AssistantRequest, AssistantResponse
from app.rag.indexer import KnowledgeIndexer
from app.streaming import run_assistant

app = FastAPI(title="贵州乌东文旅 AI 服务", version="0.1.0")


@app.get("/health")
async def health() -> dict[str, Any]:
    settings = get_settings()
    return {
        "status": "ok",
        "deepseek": "configured" if settings.deepseek_api_key else "not_configured",
        "embedding": "pending_endpoint",
        "langsmith": "configured" if settings.langsmith_tracing and settings.langsmith_api_key else "not_configured",
    }


@app.post("/api/assistant", response_model=AssistantResponse)
async def assistant(request: AssistantRequest) -> AssistantResponse:
    events: list[dict[str, Any]] = []
    card: dict[str, Any] | None = None
    async for item in run_assistant(request.model_dump()):
        events.append(item)
        if item["type"] == "card_ready":
            card = item["card"]
    return AssistantResponse(card=card or {"type": "error", "title": "助手暂不可用"}, events=events)


@app.post("/internal/index/knowledge/{document_id}")
async def index_knowledge(document_id: str, payload: dict[str, str]) -> dict[str, Any]:
    result = await KnowledgeIndexer().index(document_id, payload.get("title", "未命名资料"), payload.get("content", ""))
    return {"status": "indexed", **result}


@app.websocket("/ws/assistant")
async def assistant_socket(websocket: WebSocket) -> None:
    await websocket.accept()
    try:
        while True:
            payload = AssistantRequest.model_validate(await websocket.receive_json())
            async for item in run_assistant(payload.model_dump()):
                await websocket.send_json(item)
    except WebSocketDisconnect:
        return
