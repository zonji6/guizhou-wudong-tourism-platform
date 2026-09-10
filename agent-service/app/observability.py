from app.config import get_settings


LANGSMITH_FIELDS = frozenset(
    {
        "runState",
        "agentType",
        "retrievalMode",
        "knowledgeDependencyCount",
        "candidateCount",
        "errorCode",
    }
)


def langsmith_enabled() -> bool:
    settings = get_settings()
    return settings.langsmith_tracing and bool(settings.langsmith_api_key)


def redact_run_metadata(event: dict[str, object]) -> dict[str, object]:
    """Return only the frozen non-content LangSmith whitelist."""
    return {key: event[key] for key in LANGSMITH_FIELDS if key in event}
