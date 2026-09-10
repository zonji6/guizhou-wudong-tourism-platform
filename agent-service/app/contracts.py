from datetime import date, datetime
from typing import Annotated, Literal, Self

from pydantic import BaseModel, ConfigDict, Field, RootModel, model_validator


def _to_camel(value: str) -> str:
    head, *tail = value.split("_")
    return head + "".join(part.capitalize() for part in tail)


class StrictContractModel(BaseModel):
    """Shared v2 API boundary: strict fields with camelCase JSON aliases."""

    model_config = ConfigDict(
        alias_generator=_to_camel,
        extra="forbid",
        populate_by_name=True,
        str_strip_whitespace=True,
    )


# These names remain the v1 runtime boundary until builder/streaming are
# migrated in a later task. They do not claim v2 checkpoint safety.
LegacyCardType = Literal[
    "itinerary",
    "service_recommendation",
    "knowledge_answer",
    "clarifying_question",
    "pending_booking",
    "error",
]


class Source(BaseModel):
    title: str
    document_id: str | None = None


class AgentCard(BaseModel):
    type: LegacyCardType
    title: str
    summary: str = ""
    data: dict[str, object] = Field(default_factory=dict)
    sources: list[Source] = Field(default_factory=list)


class AssistantRequest(BaseModel):
    thread_id: str = "demo-session"
    user_text: str = ""
    page_action: str | None = None
    service_id: str | None = Field(default=None, alias="serviceId")
    people: int | None = None
    travel_date: str | None = Field(default=None, alias="travelDate")
    contact_name: str | None = Field(default=None, alias="contactName")
    contact_phone: str | None = Field(default=None, alias="contactPhone")


class AssistantResponse(BaseModel):
    card: AgentCard
    events: list[dict[str, object]] = Field(default_factory=list)


TargetType = Literal["PRODUCT", "FOOD", "STAY", "PLACE", "ROUTE_GUIDE"]
ProposalType = Literal["FOOD_ORDER", "STAY_BOOKING"]
Intent = Literal[
    "ITINERARY",
    "KNOWLEDGE",
    "PRODUCT",
    "FOOD",
    "STAY",
    "PLACE",
    "ROUTE_GUIDE",
    "ORDER_STATUS",
    "CLARIFICATION",
]
SessionStatus = Literal[
    "NEW",
    "ACTIVE",
    "WAITING_FOR_USER",
    "COMPLETED",
    "DEGRADED",
    "FAILED",
]
PageAction = Literal[
    "RECOMMEND_PRODUCT",
    "RECOMMEND_FOOD",
    "RECOMMEND_STAY",
    "BROWSE_PLACES",
    "BROWSE_ROUTE_GUIDES",
    "SHOW_MY_ORDERS",
]


class PublicTarget(StrictContractModel):
    target_type: TargetType
    target_id: str = Field(min_length=1, max_length=120)
    target_name: str = Field(min_length=1, max_length=180)


class ConfirmedConditions(StrictContractModel):
    travel_date: date | None = None
    visit_at: datetime | None = None
    check_in_date: date | None = None
    people_count: int | None = Field(default=None, gt=0)
    preferences: list[str] = Field(default_factory=list, max_length=12)
    selected_targets: list[PublicTarget] = Field(default_factory=list, max_length=12)


class PublicSource(StrictContractModel):
    title: str = Field(min_length=1, max_length=180)
    document_id: str | None = Field(default=None, max_length=120)
    source_type: Literal["KNOWLEDGE", "CATALOG", "ROUTE_GUIDE"]
    demo_data: bool = True


ActionType = Literal[
    "OPEN_DETAIL",
    "ADD_TO_ITINERARY",
    "OPEN_ORDER_CONFIRMATION",
    "OPEN_MAP",
    "OPEN_ROUTE_GUIDE",
    "RETRY",
]


class CardAction(StrictContractModel):
    action: ActionType
    label: str = Field(min_length=1, max_length=40)
    target_type: TargetType | None = None
    target_id: str | None = Field(default=None, min_length=1, max_length=120)

    @model_validator(mode="after")
    def validate_target(self) -> Self:
        target_actions = {
            "OPEN_DETAIL",
            "ADD_TO_ITINERARY",
            "OPEN_ORDER_CONFIRMATION",
            "OPEN_MAP",
            "OPEN_ROUTE_GUIDE",
        }
        if self.action in target_actions and (self.target_type is None or self.target_id is None):
            raise ValueError("该动作必须同时提供 targetType 与 targetId")
        if self.action == "RETRY" and (self.target_type is not None or self.target_id is not None):
            raise ValueError("RETRY 动作不得携带目标")
        if self.action == "OPEN_ORDER_CONFIRMATION" and self.target_type not in {"FOOD", "STAY"}:
            raise ValueError("只有餐食和住宿可以进入订单确认页")
        if self.action == "OPEN_MAP" and self.target_type != "PLACE":
            raise ValueError("地图动作只能指向地点")
        if self.action == "OPEN_ROUTE_GUIDE" and self.target_type != "ROUTE_GUIDE":
            raise ValueError("路线攻略动作只能指向路线攻略")
        return self


class PublicToolSummary(StrictContractModel):
    node_name: str = Field(min_length=1, max_length=80)
    tool_category: str | None = Field(default=None, max_length=80)
    source_titles: list[str] = Field(default_factory=list, max_length=20)
    duration_ms: int = Field(ge=0)
    final_status: str = Field(min_length=1, max_length=32)


class OrderProposal(StrictContractModel):
    proposal_type: ProposalType
    target_type: Literal["FOOD", "STAY"]
    target_id: str = Field(min_length=1, max_length=120)
    target_name: str = Field(min_length=1, max_length=180)
    visit_at: datetime | None = None
    check_in_date: date | None = None
    people_count: int = Field(gt=0)
    note: str | None = Field(default=None, max_length=300)
    source_thread_id: str = Field(min_length=1, max_length=100)
    requires_visitor_confirmation: Literal[True] = True
    demo_data: bool = True

    @model_validator(mode="after")
    def validate_proposal_semantics(self) -> Self:
        if self.proposal_type == "FOOD_ORDER":
            if self.target_type != "FOOD" or self.visit_at is None or self.check_in_date is not None:
                raise ValueError("餐食提案必须指向 FOOD，且只提供 visitAt")
        elif self.target_type != "STAY" or self.check_in_date is None or self.visit_at is not None:
            raise ValueError("住宿提案必须指向 STAY 房型，且只提供 checkInDate")
        return self


class ItineraryStop(StrictContractModel):
    time_text: str = Field(max_length=80)
    title: str = Field(min_length=1, max_length=180)
    summary: str = Field(default="", max_length=500)
    target_type: TargetType | None = None
    target_id: str | None = Field(default=None, min_length=1, max_length=120)
    demo_data: bool = True

    @model_validator(mode="after")
    def validate_target_pair(self) -> Self:
        if (self.target_type is None) != (self.target_id is None):
            raise ValueError("行程节点的 targetType 与 targetId 必须同时出现或同时省略")
        return self


class ItineraryDay(StrictContractModel):
    day: int = Field(gt=0)
    theme: str = Field(min_length=1, max_length=120)
    items: list[ItineraryStop] = Field(default_factory=list, max_length=12)


class ItineraryData(StrictContractModel):
    days: list[ItineraryDay] = Field(min_length=1, max_length=7)
    notice: str = Field(min_length=1, max_length=300)
    retrieval_mode: Literal["VECTOR", "KEYWORD_DEMO", "NONE"] = "NONE"


class RecommendationItem(StrictContractModel):
    target_type: TargetType
    target_id: str = Field(min_length=1, max_length=120)
    target_name: str = Field(min_length=1, max_length=180)
    summary: str = Field(default="", max_length=500)
    price: float | None = Field(default=None, ge=0)
    tags: list[str] = Field(default_factory=list, max_length=20)
    demo_data: bool = True


class ServiceRecommendationData(StrictContractModel):
    items: list[RecommendationItem] = Field(default_factory=list, max_length=12)
    notice: str = Field(min_length=1, max_length=300)


class KnowledgeAnswerData(StrictContractModel):
    answer: str = Field(min_length=1, max_length=2400)
    retrieval_mode: Literal["VECTOR", "KEYWORD_DEMO"]


ConditionField = Literal[
    "travelDate",
    "visitAt",
    "checkInDate",
    "peopleCount",
    "preferences",
    "target",
]


class ClarifyingQuestionData(StrictContractModel):
    required_fields: list[ConditionField] = Field(min_length=1, max_length=6)
    confirmed_conditions: ConfirmedConditions = Field(default_factory=ConfirmedConditions)


class OrderProposalData(StrictContractModel):
    proposal: OrderProposal


class ErrorData(StrictContractModel):
    code: str = Field(min_length=1, max_length=64)
    retryable: bool = False
    demo_available: bool = False


class CardBase(StrictContractModel):
    card_version: Literal["2.0"] = "2.0"
    title: str = Field(min_length=1, max_length=180)
    summary: str = Field(default="", max_length=1200)
    sources: list[PublicSource] = Field(default_factory=list, max_length=20)
    actions: list[CardAction] = Field(default_factory=list, max_length=10)


class ItineraryCard(CardBase):
    type: Literal["itinerary"] = "itinerary"
    data: ItineraryData


class ServiceRecommendationCard(CardBase):
    type: Literal["service_recommendation"] = "service_recommendation"
    data: ServiceRecommendationData


class KnowledgeAnswerCard(CardBase):
    type: Literal["knowledge_answer"] = "knowledge_answer"
    data: KnowledgeAnswerData
    sources: list[PublicSource] = Field(min_length=1, max_length=20)


class ClarifyingQuestionCard(CardBase):
    type: Literal["clarifying_question"] = "clarifying_question"
    data: ClarifyingQuestionData


class PendingBookingCard(CardBase):
    type: Literal["pending_booking"] = "pending_booking"
    data: OrderProposalData


class ErrorCard(CardBase):
    type: Literal["error"] = "error"
    data: ErrorData


CardVariant = Annotated[
    ItineraryCard
    | ServiceRecommendationCard
    | KnowledgeAnswerCard
    | ClarifyingQuestionCard
    | PendingBookingCard
    | ErrorCard,
    Field(discriminator="type"),
]


class AssistantCardV2(RootModel[CardVariant]):
    """Strict discriminated v2 card; serializes directly to the wrapped card."""


class AssistantRequestV2(StrictContractModel):
    thread_id: str = Field(min_length=1, max_length=100)
    user_text: str = Field(default="", max_length=4000)
    page_action: PageAction | None = None
    selected_target: PublicTarget | None = None
    conditions: ConfirmedConditions = Field(default_factory=ConfirmedConditions)


class TaskStartedEvent(StrictContractModel):
    type: Literal["task_started"] = "task_started"
    thread_id: str = Field(min_length=1, max_length=100)


ProgressStage = Literal["UNDERSTANDING", "RETRIEVING", "PLANNING", "PREPARING_RESULT"]


class ProgressEvent(StrictContractModel):
    type: Literal["progress"] = "progress"
    stage: ProgressStage
    message: str = Field(min_length=1, max_length=160)


class ToolSummaryEvent(StrictContractModel):
    type: Literal["tool_summary"] = "tool_summary"
    summary: PublicToolSummary


class CardReadyEvent(StrictContractModel):
    type: Literal["card_ready"] = "card_ready"
    card: AssistantCardV2


class CompletedEvent(StrictContractModel):
    type: Literal["completed"] = "completed"


class FailedEvent(StrictContractModel):
    type: Literal["failed"] = "failed"
    code: str = Field(min_length=1, max_length=64)


StreamEventVariant = Annotated[
    TaskStartedEvent | ProgressEvent | ToolSummaryEvent | CardReadyEvent | CompletedEvent | FailedEvent,
    Field(discriminator="type"),
]


class AssistantStreamEventV2(RootModel[StreamEventVariant]):
    """Strict public stream event; never carries raw node inputs or exceptions."""


class AssistantResponseV2(StrictContractModel):
    card: AssistantCardV2
    events: list[AssistantStreamEventV2] = Field(default_factory=list, max_length=80)
