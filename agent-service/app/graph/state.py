from typing import Any, TypedDict


class AgentState(TypedDict, total=False):
    thread_id: str
    user_text: str
    page_action: str | None
    service_id: str | None
    people: int | None
    travel_date: str | None
    intent: str
    retrieved_chunks: list[dict[str, Any]]
    tool_results: list[dict[str, Any]]
    card: dict[str, Any]
    events: list[dict[str, Any]]
    error: str | None
