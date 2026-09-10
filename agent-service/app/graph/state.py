from __future__ import annotations

from dataclasses import dataclass
from typing import Literal, TypedDict

from app.rag.retriever import RetrievalBundle
from app.v3_contracts import (
    AssistantCardV3,
    CandidateType,
    FoodCandidatePayload,
    ItineraryContent,
    PublicToolSummary,
    StayCandidatePayload,
)


@dataclass(frozen=True, slots=True)
class CandidatePlan:
    candidate_type: CandidateType
    payload: ItineraryContent | FoodCandidatePayload | StayCandidatePayload
    title: str
    summary: str
    demo_data: bool


@dataclass(frozen=True, slots=True)
class ProposedResult:
    intent: Literal["KNOWLEDGE", "SERVICE", "ITINERARY", "FOOD_DRAFT", "STAY_DRAFT"]
    card: AssistantCardV3 | None
    candidate_plan: CandidatePlan | None
    retrieval: RetrievalBundle
    tool_summaries: list[PublicToolSummary]


class WorkflowState(TypedDict, total=False):
    agent_type: Literal["KNOWLEDGE_GUIDE", "SERVICE_RECOMMENDER", "ITINERARY_PLANNER"]
    result: ProposedResult
