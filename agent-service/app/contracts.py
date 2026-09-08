from typing import Any, Literal

from pydantic import BaseModel, Field


CardType = Literal[
    "itinerary",
    "service_recommendation",
    "knowledge_answer",
    "clarifying_question",
    "pending_booking",
    "error",
]


class Source(BaseModel):
    title: str
    document_id: int | None = None


class AgentCard(BaseModel):
    type: CardType
    title: str
    summary: str = ""
    data: dict[str, Any] = Field(default_factory=dict)
    sources: list[Source] = Field(default_factory=list)


class AssistantRequest(BaseModel):
    thread_id: str = "demo-session"
    user_text: str = ""
    page_action: str | None = None
    service_id: str | None = None
    people: int | None = None
    travel_date: str | None = Field(default=None, alias="travelDate")


class AssistantResponse(BaseModel):
    card: AgentCard
    events: list[dict[str, Any]] = Field(default_factory=list)
