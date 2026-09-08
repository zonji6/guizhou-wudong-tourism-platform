from operator import add
from typing import Annotated, Any, TypedDict


class AgentState(TypedDict, total=False):
    thread_id: str
    user_text: str
    messages: Annotated[list[dict[str, str]], add]
    page_action: str | None
    service_id: str | None
    people: int | None
    travel_date: str | None
    contact_name: str | None
    contact_phone: str | None
    intent: str
    retrieved_chunks: list[dict[str, Any]]
    services: list[dict[str, Any]]
    card: dict[str, Any]
    error: str | None
