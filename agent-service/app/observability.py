from app.config import get_settings


def langsmith_enabled() -> bool:
    settings = get_settings()
    return settings.langsmith_tracing and bool(settings.langsmith_api_key)


def redact_event(event: dict) -> dict:
    """后台只展示节点、工具、状态和耗时，不保存提示词或联系方式。"""
    allowed = {"type", "node", "tool", "status", "duration_ms", "source_count"}
    return {key: value for key, value in event.items() if key in allowed}
