from __future__ import annotations

from datetime import date, datetime
from typing import Annotated, Literal, Self

from pydantic import BaseModel, ConfigDict, Field, RootModel, StringConstraints, field_validator, model_validator


ASSISTANT_CONTRACT_VERSION = "assistant-card-v3-draft-r1"
TOURISM_CONTRACT_VERSION = "tourism-api-v3-draft-r3"
EVENT_VERSION = "assistant-event-v3-draft-r1"
STATE_VERSION = "assistant-session-state-v3-draft-r1"
CHECKPOINT_VERSION = "assistant-checkpoint-v3-draft-r1"
CARD_VERSION = "3.0"
WS_AUTH_VERSION = "wudong-ws-auth-v1"


def _to_camel(value: str) -> str:
    head, *tail = value.split("_")
    return head + "".join(part.capitalize() for part in tail)


class StrictModel(BaseModel):
    model_config = ConfigDict(
        alias_generator=_to_camel,
        extra="forbid",
        populate_by_name=True,
        strict=True,
        str_strip_whitespace=True,
    )


CanonicalUuid = Annotated[
    str,
    StringConstraints(pattern=r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"),
]
Sha256Digest = Annotated[str, StringConstraints(pattern=r"^sha256:[0-9a-f]{64}$")]
MoneyAmount = Annotated[str, StringConstraints(pattern=r"^(0|[1-9][0-9]{0,9})\.[0-9]{2}$")]
UtcTimestamp = Annotated[str, StringConstraints(pattern=r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$")]
LocalDate = Annotated[str, StringConstraints(pattern=r"^\d{4}-\d{2}-\d{2}$")]
LocalDateTime = Annotated[str, StringConstraints(pattern=r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$")]
PositiveInt = Annotated[int, Field(strict=True, gt=0)]
NonNegativeInt = Annotated[int, Field(strict=True, ge=0)]


class TemporalModel(StrictModel):
    @field_validator("*", mode="after")
    @classmethod
    def validate_calendar_values(cls, value: object, info):
        if value is None or not isinstance(value, str):
            return value
        if info.field_name in {"travel_date", "check_in_date", "check_out_date"}:
            try:
                date.fromisoformat(value)
            except ValueError as exc:
                raise ValueError("日期不是有效日历日期") from exc
        elif info.field_name == "visit_at":
            try:
                datetime.strptime(value, "%Y-%m-%dT%H:%M:%S")
            except ValueError as exc:
                raise ValueError("本地日期时间格式无效") from exc
        elif info.field_name in {
            "created_at",
            "expires_at",
            "started_at",
            "requested_at",
            "finished_at",
            "emitted_at",
            "committed_at",
            "logical_expires_at",
            "cancel_requested_at",
            "resolved_at",
            "checked_at",
            "auth_expires_at",
        }:
            try:
                datetime.strptime(value, "%Y-%m-%dT%H:%M:%SZ")
            except ValueError as exc:
                raise ValueError("UTC 时间格式无效") from exc
        return value


TargetType = Literal["PRODUCT", "FOOD", "STAY", "PLACE", "ROUTE_GUIDE"]
RetrievalMode = Literal["NONE", "KEYWORD_DEMO", "VECTOR"]
KnowledgeRetrievalMode = Literal["KEYWORD_DEMO", "VECTOR"]
AgentType = Literal["KNOWLEDGE_GUIDE", "SERVICE_RECOMMENDER", "ITINERARY_PLANNER"]
CandidateType = Literal["ITINERARY", "FOOD_DRAFT", "STAY_DRAFT"]
CandidateAction = Literal["CREATE", "UPDATE"]
OwnerKind = Literal["USER", "ANONYMOUS"]
RunState = Literal["RUNNING", "CANCEL_REQUESTED", "STOPPED", "INTERRUPTED", "COMPLETED", "FAILED"]


class PublicTarget(StrictModel):
    target_type: TargetType = Field(...)
    target_id: CanonicalUuid = Field(...)
    target_name: str = Field(..., min_length=1, max_length=180)


class FoodItemSelection(StrictModel):
    food_item_id: CanonicalUuid = Field(...)
    quantity: PositiveInt = Field(...)


class MerchantItemsCondition(StrictModel):
    merchant_id: CanonicalUuid = Field(...)
    items: list[FoodItemSelection] = Field(..., min_length=1, max_length=50)

    @model_validator(mode="after")
    def unique_food_items(self) -> Self:
        values = [item.food_item_id for item in self.items]
        if len(values) != len(set(values)):
            raise ValueError("foodItemId 不得重复")
        return self


class ConfirmedConditionsV3(TemporalModel):
    travel_date: LocalDate | None = Field(...)
    visit_at: LocalDateTime | None = Field(...)
    check_in_date: LocalDate | None = Field(...)
    check_out_date: LocalDate | None = Field(...)
    room_count: PositiveInt | None = Field(...)
    people_count: PositiveInt | None = Field(...)
    preferences: list[Annotated[str, StringConstraints(min_length=1, max_length=120)]] = Field(..., max_length=12)
    selected_targets: list[PublicTarget] = Field(..., max_length=12)
    merchant_items: MerchantItemsCondition | None = Field(...)

    @model_validator(mode="after")
    def validate_unique_values(self) -> Self:
        if len(self.preferences) != len(set(self.preferences)):
            raise ValueError("preferences 不得重复")
        keys = [(item.target_type, item.target_id) for item in self.selected_targets]
        if len(keys) != len(set(keys)):
            raise ValueError("selectedTargets 不得重复")
        if self.check_in_date and self.check_out_date and self.check_out_date <= self.check_in_date:
            raise ValueError("checkOutDate 必须晚于 checkInDate")
        return self


class KnowledgeReference(StrictModel):
    source_title: str = Field(..., min_length=1, max_length=180)
    detail_path: Annotated[
        str,
        StringConstraints(pattern=r"^/api/knowledge-documents/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"),
    ] = Field(...)


class ReferencePrice(StrictModel):
    amount: MoneyAmount = Field(...)
    currency: Literal["CNY"] = Field(...)
    unit: Literal["ITEM", "PORTION", "ROOM_NIGHT"] = Field(...)
    source_title: str = Field(..., min_length=1, max_length=180)


class DemoPrice(StrictModel):
    amount: MoneyAmount = Field(...)
    currency: Literal["CNY"] = Field(...)
    unit: Literal["ITEM", "PORTION", "ROOM_NIGHT"] = Field(...)
    simulation_note: str = Field(..., min_length=1, max_length=240)


class CandidateRef(StrictModel):
    thread_id: CanonicalUuid = Field(...)
    candidate_id: CanonicalUuid = Field(...)
    candidate_version: PositiveInt = Field(...)


class BaseResource(StrictModel):
    resource_type: CandidateType = Field(...)
    resource_id: CanonicalUuid = Field(...)
    resource_version: PositiveInt = Field(...)


class CandidateMeta(TemporalModel):
    candidate_ref: CandidateRef = Field(...)
    candidate_type: CandidateType = Field(...)
    adoption_action: CandidateAction = Field(...)
    base_resource: BaseResource | None = Field(...)
    created_at: UtcTimestamp = Field(...)
    expires_at: UtcTimestamp = Field(...)

    @model_validator(mode="after")
    def validate_action_and_base(self) -> Self:
        if self.adoption_action == "CREATE" and self.base_resource is not None:
            raise ValueError("CREATE 的 baseResource 必须为 null")
        if self.adoption_action == "UPDATE":
            if self.base_resource is None or self.base_resource.resource_type != self.candidate_type:
                raise ValueError("UPDATE 的 baseResource 必须存在且类型一致")
        if self.expires_at < self.created_at:
            raise ValueError("expiresAt 不得早于 createdAt")
        return self


class OpenDetailAction(StrictModel):
    action: Literal["OPEN_DETAIL"] = Field(...)
    label: str = Field(..., min_length=1, max_length=40)
    target_type: TargetType = Field(...)
    target_id: CanonicalUuid = Field(...)


class OpenMapAction(StrictModel):
    action: Literal["OPEN_MAP"] = Field(...)
    label: str = Field(..., min_length=1, max_length=40)
    target_type: Literal["PLACE"] = Field(...)
    target_id: CanonicalUuid = Field(...)


class OpenRouteGuideAction(StrictModel):
    action: Literal["OPEN_ROUTE_GUIDE"] = Field(...)
    label: str = Field(..., min_length=1, max_length=40)
    target_type: Literal["ROUTE_GUIDE"] = Field(...)
    target_id: CanonicalUuid = Field(...)


class SaveItineraryAction(StrictModel):
    action: Literal["SAVE_ITINERARY"] = Field(...)
    label: str = Field(..., min_length=1, max_length=40)
    candidate_ref: CandidateRef = Field(...)


class AdoptFoodDraftAction(StrictModel):
    action: Literal["ADOPT_FOOD_DRAFT"] = Field(...)
    label: str = Field(..., min_length=1, max_length=40)
    candidate_ref: CandidateRef = Field(...)


class AdoptStayDraftAction(StrictModel):
    action: Literal["ADOPT_STAY_DRAFT"] = Field(...)
    label: str = Field(..., min_length=1, max_length=40)
    candidate_ref: CandidateRef = Field(...)


class RetryAction(StrictModel):
    action: Literal["RETRY"] = Field(...)
    label: str = Field(..., min_length=1, max_length=40)


CardActionV3 = Annotated[
    OpenDetailAction
    | OpenMapAction
    | OpenRouteGuideAction
    | SaveItineraryAction
    | AdoptFoodDraftAction
    | AdoptStayDraftAction
    | RetryAction,
    Field(discriminator="action"),
]


class ItineraryStop(StrictModel):
    sequence: PositiveInt = Field(...)
    target_type: TargetType | None = Field(...)
    target_id: CanonicalUuid | None = Field(...)
    title: str = Field(..., min_length=1, max_length=180)
    note: str | None = Field(..., max_length=500)

    @model_validator(mode="after")
    def validate_target_pair(self) -> Self:
        if (self.target_type is None) != (self.target_id is None):
            raise ValueError("targetType 与 targetId 必须同时为 null 或同时存在")
        return self


class ItineraryDay(StrictModel):
    day: PositiveInt = Field(...)
    theme: str | None = Field(..., max_length=80)
    stops: list[ItineraryStop] = Field(..., max_length=30)

    @model_validator(mode="after")
    def validate_stop_sequence(self) -> Self:
        if any(item.sequence != index for index, item in enumerate(self.stops, 1)):
            raise ValueError("stop.sequence 必须从 1 连续递增")
        return self


class ItineraryContent(TemporalModel):
    title: str = Field(..., min_length=1, max_length=80)
    travel_date: LocalDate | None = Field(...)
    people_count: PositiveInt | None = Field(...)
    days: list[ItineraryDay] = Field(..., min_length=1, max_length=30)

    @model_validator(mode="after")
    def validate_day_sequence(self) -> Self:
        if any(item.day != index for index, item in enumerate(self.days, 1)):
            raise ValueError("day 必须从 1 连续递增")
        return self


class RecommendationItem(StrictModel):
    target_type: TargetType = Field(...)
    target_id: CanonicalUuid = Field(...)
    target_name: str = Field(..., min_length=1, max_length=180)
    summary: str = Field(..., max_length=500)
    reference_price: ReferencePrice | None = Field(...)
    demo_price: DemoPrice | None = Field(...)
    tags: list[Annotated[str, StringConstraints(min_length=1, max_length=120)]] = Field(..., max_length=20)
    demo_data: bool = Field(...)

    @model_validator(mode="after")
    def validate_non_price_target(self) -> Self:
        if self.target_type in {"PLACE", "ROUTE_GUIDE"} and (
            self.reference_price is not None or self.demo_price is not None
        ):
            raise ValueError("地点与路线攻略不得携带价格")
        return self


class FoodCandidatePayload(TemporalModel):
    merchant_id: CanonicalUuid = Field(...)
    items: list[FoodItemSelection] = Field(..., min_length=1, max_length=50)
    visit_at: LocalDateTime | None = Field(...)
    people_count: PositiveInt | None = Field(...)

    @model_validator(mode="after")
    def unique_food_items(self) -> Self:
        ids = [item.food_item_id for item in self.items]
        if len(ids) != len(set(ids)):
            raise ValueError("foodItemId 不得重复")
        return self


class StayCandidatePayload(TemporalModel):
    room_type_id: CanonicalUuid = Field(...)
    check_in_date: LocalDate | None = Field(...)
    check_out_date: LocalDate | None = Field(...)
    room_count: PositiveInt | None = Field(...)
    people_count: PositiveInt | None = Field(...)

    @model_validator(mode="after")
    def validate_date_range(self) -> Self:
        if self.check_in_date and self.check_out_date and self.check_out_date <= self.check_in_date:
            raise ValueError("checkOutDate 必须晚于 checkInDate")
        return self


class CardBase(StrictModel):
    card_version: Literal["3.0"] = Field(...)
    title: str = Field(..., min_length=1, max_length=80)
    summary: str = Field(..., max_length=800)
    references: list[KnowledgeReference] = Field(..., max_length=50)
    actions: list[CardActionV3] = Field(..., max_length=20)


class ItineraryData(StrictModel):
    content: ItineraryContent = Field(...)
    candidate: CandidateMeta | None = Field(...)
    retrieval_mode: RetrievalMode = Field(...)
    demo_data: bool = Field(...)
    notice: str = Field(..., min_length=1, max_length=300)


class ItineraryCard(CardBase):
    type: Literal["itinerary"] = Field(...)
    data: ItineraryData = Field(...)

    @model_validator(mode="after")
    def validate_candidate_action(self) -> Self:
        save_actions = [item for item in self.actions if item.action == "SAVE_ITINERARY"]
        if self.data.candidate is None:
            if save_actions:
                raise ValueError("无候选的行程不得提供保存动作")
        else:
            candidate = self.data.candidate
            if candidate.candidate_type != "ITINERARY" or len(save_actions) != 1:
                raise ValueError("行程候选与保存动作不匹配")
            if save_actions[0].candidate_ref != candidate.candidate_ref:
                raise ValueError("保存动作必须引用同一候选")
        if self.data.retrieval_mode == "NONE" and self.references:
            raise ValueError("NONE 模式不得携带知识引用")
        return self


class ServiceRecommendationData(StrictModel):
    items: list[RecommendationItem] = Field(..., min_length=1, max_length=20)
    retrieval_mode: RetrievalMode = Field(...)
    notice: str = Field(..., min_length=1, max_length=300)


class ServiceRecommendationCard(CardBase):
    type: Literal["service_recommendation"] = Field(...)
    data: ServiceRecommendationData = Field(...)

    @model_validator(mode="after")
    def validate_references(self) -> Self:
        if self.data.retrieval_mode == "NONE" and self.references:
            raise ValueError("NONE 模式不得携带知识引用")
        return self


class KnowledgeAnswerData(StrictModel):
    answer: str = Field(..., min_length=1, max_length=2400)
    retrieval_mode: KnowledgeRetrievalMode = Field(...)
    demo_data: bool = Field(...)


class KnowledgeAnswerCard(CardBase):
    type: Literal["knowledge_answer"] = Field(...)
    data: KnowledgeAnswerData = Field(...)
    references: list[KnowledgeReference] = Field(..., min_length=1, max_length=50)


ConditionField = Literal[
    "travelDate",
    "visitAt",
    "checkInDate",
    "checkOutDate",
    "roomCount",
    "peopleCount",
    "preferences",
    "target",
    "merchantItems",
]


class ClarifyingQuestionData(StrictModel):
    required_fields: list[ConditionField] = Field(..., min_length=1, max_length=9)
    confirmed_conditions: ConfirmedConditionsV3 = Field(...)

    @model_validator(mode="after")
    def unique_required_fields(self) -> Self:
        if len(self.required_fields) != len(set(self.required_fields)):
            raise ValueError("requiredFields 不得重复")
        return self


class ClarifyingQuestionCard(CardBase):
    type: Literal["clarifying_question"] = Field(...)
    data: ClarifyingQuestionData = Field(...)

    @model_validator(mode="after")
    def no_private_actions_or_sources(self) -> Self:
        if self.references or any(hasattr(item, "candidate_ref") for item in self.actions):
            raise ValueError("追问卡不得携带知识引用或候选动作")
        return self


class PendingBookingData(StrictModel):
    candidate: CandidateMeta = Field(...)
    proposal: FoodCandidatePayload | StayCandidatePayload = Field(...)
    retrieval_mode: RetrievalMode = Field(...)
    demo_data: bool = Field(...)
    notice: str = Field(..., min_length=1, max_length=300)

    @model_validator(mode="after")
    def validate_proposal_type(self) -> Self:
        if self.candidate.candidate_type == "FOOD_DRAFT" and not isinstance(self.proposal, FoodCandidatePayload):
            raise ValueError("FOOD_DRAFT 必须使用餐食 payload")
        if self.candidate.candidate_type == "STAY_DRAFT" and not isinstance(self.proposal, StayCandidatePayload):
            raise ValueError("STAY_DRAFT 必须使用住宿 payload")
        if self.candidate.candidate_type == "ITINERARY":
            raise ValueError("pending_booking 不接受行程候选")
        return self


class PendingBookingCard(CardBase):
    type: Literal["pending_booking"] = Field(...)
    data: PendingBookingData = Field(...)

    @model_validator(mode="after")
    def validate_candidate_action(self) -> Self:
        expected = "ADOPT_FOOD_DRAFT" if self.data.candidate.candidate_type == "FOOD_DRAFT" else "ADOPT_STAY_DRAFT"
        actions = [item for item in self.actions if item.action in {"ADOPT_FOOD_DRAFT", "ADOPT_STAY_DRAFT"}]
        if len(actions) != 1 or actions[0].action != expected:
            raise ValueError("待采用方案动作与候选类型不匹配")
        if actions[0].candidate_ref != self.data.candidate.candidate_ref:
            raise ValueError("采用动作必须引用同一候选")
        if self.data.retrieval_mode == "NONE" and self.references:
            raise ValueError("NONE 模式不得携带知识引用")
        return self


class ErrorData(StrictModel):
    code: Annotated[str, StringConstraints(pattern=r"^[A-Z0-9_]{1,64}$")] = Field(...)
    retryable: bool = Field(...)


class ErrorCard(CardBase):
    type: Literal["error"] = Field(...)
    data: ErrorData = Field(...)

    @model_validator(mode="after")
    def validate_error_shape(self) -> Self:
        if self.references or any(item.action != "RETRY" for item in self.actions):
            raise ValueError("错误卡只能提供 RETRY 动作")
        return self


CardVariant = Annotated[
    ItineraryCard
    | ServiceRecommendationCard
    | KnowledgeAnswerCard
    | ClarifyingQuestionCard
    | PendingBookingCard
    | ErrorCard,
    Field(discriminator="type"),
]


class AssistantCardV3(RootModel[CardVariant]):
    model_config = ConfigDict(strict=True)


PageAction = Literal[
    "RECOMMEND_PRODUCT",
    "RECOMMEND_FOOD",
    "RECOMMEND_STAY",
    "BROWSE_PLACES",
    "BROWSE_ROUTE_GUIDES",
    "SHOW_MY_ORDERS",
]


class VersionedRequest(StrictModel):
    assistant_contract_version: Literal["assistant-card-v3-draft-r1"] = Field(...)
    tourism_contract_version: Literal["tourism-api-v3-draft-r3"] = Field(...)


class SessionStateRequest(VersionedRequest):
    type: Literal["session_state_request"] = Field(...)
    thread_id: CanonicalUuid | None = Field(...)
    run_id: CanonicalUuid | None = Field(...)
    last_event_sequence: NonNegativeInt = Field(...)


class GenerateRequest(VersionedRequest):
    type: Literal["generate"] = Field(...)
    client_request_id: CanonicalUuid = Field(...)
    thread_id: CanonicalUuid = Field(...)
    expected_checkpoint_revision: PositiveInt | None = Field(...)
    user_text: str = Field(..., max_length=2000)
    page_action: PageAction | None = Field(...)
    selected_target: PublicTarget | None = Field(...)
    base_resource: BaseResource | None = Field(...)
    conditions: ConfirmedConditionsV3 = Field(...)
    retrieval_mode: RetrievalMode = Field(...)

    @model_validator(mode="after")
    def validate_input(self) -> Self:
        if not self.user_text.strip() and self.page_action is None:
            raise ValueError("userText 与 pageAction 至少一个有效")
        return self


class CancelRunRequest(VersionedRequest):
    type: Literal["cancel_run"] = Field(...)
    cancel_request_id: CanonicalUuid = Field(...)
    thread_id: CanonicalUuid = Field(...)
    run_id: CanonicalUuid = Field(...)


BusinessRequest = Annotated[SessionStateRequest | GenerateRequest | CancelRunRequest, Field(discriminator="type")]


class UserAuthFrame(StrictModel):
    type: Literal["auth"] = Field(...)
    auth_version: Literal["wudong-ws-auth-v1"] = Field(...)
    contract_version: Literal["tourism-api-v3-draft-r3"] = Field(...)
    mode: Literal["USER"] = Field(...)
    access_token: str = Field(..., min_length=1, max_length=8192)


class AnonymousWebAuthFrame(StrictModel):
    type: Literal["auth"] = Field(...)
    auth_version: Literal["wudong-ws-auth-v1"] = Field(...)
    contract_version: Literal["tourism-api-v3-draft-r3"] = Field(...)
    mode: Literal["ANONYMOUS_WEB"] = Field(...)


class AnonymousMiniAuthFrame(StrictModel):
    type: Literal["auth"] = Field(...)
    auth_version: Literal["wudong-ws-auth-v1"] = Field(...)
    contract_version: Literal["tourism-api-v3-draft-r3"] = Field(...)
    mode: Literal["ANONYMOUS_MINI"] = Field(...)
    anonymous_credential: str = Field(..., min_length=1, max_length=512)


class AuthOk(TemporalModel):
    type: Literal["auth_ok"] = Field(...)
    auth_version: Literal["wudong-ws-auth-v1"] = Field(...)
    contract_version: Literal["tourism-api-v3-draft-r3"] = Field(...)
    connection_id: CanonicalUuid = Field(...)
    mode: Literal["USER", "ANONYMOUS"] = Field(...)
    auth_expires_at: UtcTimestamp = Field(...)


class AuthFailed(StrictModel):
    type: Literal["auth_failed"] = Field(...)
    auth_version: Literal["wudong-ws-auth-v1"] = Field(...)
    contract_version: Literal["tourism-api-v3-draft-r3"] = Field(...)
    code: Annotated[str, StringConstraints(pattern=r"^[A-Z0-9_]{1,64}$")] = Field(...)
    message: str = Field(..., min_length=1, max_length=120)
    retryable: bool = Field(...)
    retry_after_seconds: PositiveInt | None = Field(default=None)


class KnowledgeDependency(StrictModel):
    document_id: CanonicalUuid = Field(...)
    build_id: CanonicalUuid = Field(...)
    snapshot_hash: Sha256Digest = Field(...)


class KnowledgeContext(StrictModel):
    retrieval_mode: KnowledgeRetrievalMode = Field(...)
    config_hash: Sha256Digest = Field(...)


CandidatePayload = ItineraryContent | FoodCandidatePayload | StayCandidatePayload


class CandidateRecord(TemporalModel):
    candidate_ref: CandidateRef = Field(...)
    candidate_type: CandidateType = Field(...)
    adoption_action: CandidateAction = Field(...)
    base_resource: BaseResource | None = Field(...)
    source_owner_kind: OwnerKind = Field(...)
    created_at: UtcTimestamp = Field(...)
    expires_at: UtcTimestamp = Field(...)
    candidate_digest: Sha256Digest = Field(...)
    knowledge_context: KnowledgeContext | None = Field(...)
    knowledge_dependencies: list[KnowledgeDependency] = Field(..., max_length=50)
    payload: CandidatePayload = Field(...)

    @model_validator(mode="after")
    def validate_candidate(self) -> Self:
        if self.adoption_action == "CREATE" and self.base_resource is not None:
            raise ValueError("CREATE 候选不得有 baseResource")
        if self.adoption_action == "UPDATE" and (
            self.base_resource is None or self.base_resource.resource_type != self.candidate_type
        ):
            raise ValueError("UPDATE 候选必须绑定同类型 baseResource")
        if bool(self.knowledge_dependencies) != (self.knowledge_context is not None):
            raise ValueError("knowledgeContext 与 knowledgeDependencies 不一致")
        expected_type = {
            "ITINERARY": ItineraryContent,
            "FOOD_DRAFT": FoodCandidatePayload,
            "STAY_DRAFT": StayCandidatePayload,
        }[self.candidate_type]
        if not isinstance(self.payload, expected_type):
            raise ValueError("候选类型与 payload 不匹配")
        return self


class CandidateResolveRequest(StrictModel):
    candidate_ref: CandidateRef = Field(...)
    expected_candidate_type: CandidateType = Field(...)
    expected_action: CandidateAction = Field(...)
    expected_base: BaseResource | None = Field(...)

    @model_validator(mode="after")
    def validate_expected_base(self) -> Self:
        if self.expected_action == "CREATE" and self.expected_base is not None:
            raise ValueError("CREATE 的 expectedBase 必须为 null")
        if self.expected_action == "UPDATE" and (
            self.expected_base is None or self.expected_base.resource_type != self.expected_candidate_type
        ):
            raise ValueError("UPDATE 的 expectedBase 无效")
        return self


class CandidateResolveResponse(CandidateRecord):
    resolved_at: UtcTimestamp = Field(...)


class PublicToolSummary(StrictModel):
    tool_category: Literal["CATALOG", "KNOWLEDGE", "PERSONAL_PLANNING"] = Field(...)
    operation_code: Literal[
        "SEARCH_CATALOG",
        "SEARCH_KNOWLEDGE",
        "VERIFY_KNOWLEDGE",
        "READ_PERSONAL_PLANNING",
    ] = Field(...)
    final_status: Literal["SUCCESS", "EMPTY", "UNAVAILABLE"] = Field(...)
    item_count: NonNegativeInt = Field(...)
    duration_ms: NonNegativeInt = Field(...)


class LastCommittedCard(TemporalModel):
    card_revision: PositiveInt = Field(...)
    card: AssistantCardV3 = Field(...)
    knowledge_context: KnowledgeContext | None = Field(...)
    knowledge_dependencies: list[KnowledgeDependency] = Field(..., max_length=50)
    committed_at: UtcTimestamp = Field(...)


class SolutionSnapshot(TemporalModel):
    solution_revision: PositiveInt = Field(...)
    card: AssistantCardV3 = Field(...)
    candidate_ref: CandidateRef = Field(...)
    candidate_type: CandidateType = Field(...)
    adoption_action: CandidateAction = Field(...)
    base_resource: BaseResource | None = Field(...)
    created_at: UtcTimestamp = Field(...)
    expires_at: UtcTimestamp = Field(...)
    knowledge_context: KnowledgeContext | None = Field(...)
    knowledge_dependencies: list[KnowledgeDependency] = Field(..., max_length=50)


class PersistentStateV3(StrictModel):
    intent: Literal["UNKNOWN", "KNOWLEDGE", "SERVICE", "ITINERARY", "FOOD_DRAFT", "STAY_DRAFT"] = Field(...)
    confirmed_conditions: ConfirmedConditionsV3 = Field(...)
    conversation_summary: str = Field(..., max_length=1000)
    last_committed_card: LastCommittedCard | None = Field(...)
    current_solution: SolutionSnapshot | None = Field(...)
    previous_solution: SolutionSnapshot | None = Field(...)
    public_tool_summaries: list[PublicToolSummary] = Field(..., max_length=20)
    session_status: Literal["NEW", "ACTIVE", "WAITING_FOR_USER", "DEGRADED"] = Field(...)


class CheckpointEnvelopeV3(TemporalModel):
    assistant_contract_version: Literal["assistant-card-v3-draft-r1"] = Field(...)
    tourism_contract_version: Literal["tourism-api-v3-draft-r3"] = Field(...)
    checkpoint_version: Literal["assistant-checkpoint-v3-draft-r1"] = Field(...)
    checkpoint_revision: PositiveInt = Field(...)
    parent_checkpoint_revision: PositiveInt | None = Field(...)
    committed_by_run_id: CanonicalUuid = Field(...)
    committed_by_fence_epoch: PositiveInt = Field(...)
    created_at: UtcTimestamp = Field(...)
    state: PersistentStateV3 = Field(...)
    checkpoint_digest: Sha256Digest = Field(...)


class EmptySolutionSlot(StrictModel):
    slot: Literal["CURRENT", "PREVIOUS"] = Field(...)
    status: Literal["EMPTY", "EXPIRED", "VERSION_UNAVAILABLE", "LOOKUP_UNAVAILABLE", "INCOMPATIBLE"] = Field(...)
    solution: None = Field(...)
    code: str | None = Field(...)


class AvailableSolutionSlot(StrictModel):
    slot: Literal["CURRENT", "PREVIOUS"] = Field(...)
    status: Literal["AVAILABLE"] = Field(...)
    solution: AssistantCardV3 = Field(...)
    code: None = Field(...)


PublicSolutionSlot = EmptySolutionSlot | AvailableSolutionSlot


class RunSnapshot(TemporalModel):
    run_id: CanonicalUuid = Field(...)
    run_state: RunState = Field(...)
    last_event_sequence: NonNegativeInt = Field(...)
    started_at: UtcTimestamp = Field(...)
    cancel_requested_at: UtcTimestamp | None = Field(...)
    finished_at: UtcTimestamp | None = Field(...)
    error_code: str | None = Field(...)


class SessionState(TemporalModel):
    assistant_contract_version: Literal["assistant-card-v3-draft-r1"] = Field(...)
    tourism_contract_version: Literal["tourism-api-v3-draft-r3"] = Field(...)
    state_version: Literal["assistant-session-state-v3-draft-r1"] = Field(...)
    type: Literal["session_state"] = Field(...)
    thread_id: CanonicalUuid = Field(...)
    logical_expires_at: UtcTimestamp = Field(...)
    checkpoint_revision: PositiveInt | None = Field(...)
    run: RunSnapshot | None = Field(...)
    can_start_new_run: bool = Field(...)
    last_card_status: Literal["EMPTY", "AVAILABLE", "KNOWLEDGE_UPDATED", "LOOKUP_UNAVAILABLE", "INCOMPATIBLE"] = Field(...)
    last_card: AssistantCardV3 | None = Field(...)
    current_solution: PublicSolutionSlot = Field(...)
    previous_solution: PublicSolutionSlot = Field(...)


class RunStartedData(TemporalModel):
    agent_type: AgentType = Field(...)
    retrieval_mode: RetrievalMode = Field(...)
    started_at: UtcTimestamp = Field(...)
    checkpoint_revision: PositiveInt | None = Field(...)


class ProgressData(StrictModel):
    stage: Literal[
        "UNDERSTANDING",
        "RETRIEVING",
        "VERIFYING_KNOWLEDGE",
        "PLANNING",
        "PREPARING_RESULT",
        "PERSISTING_RESULT",
    ] = Field(...)
    status: Literal["STARTED", "COMPLETED", "SKIPPED"] = Field(...)
    completed_units: NonNegativeInt = Field(...)
    total_units: PositiveInt | None = Field(...)


class ToolSummaryData(PublicToolSummary):
    pass


class CancelRequestedData(TemporalModel):
    requested_at: UtcTimestamp = Field(...)
    display_code: Literal["CANCEL_REQUESTED"] = Field(...)


class CardReadyData(StrictModel):
    card: AssistantCardV3 = Field(...)
    checkpoint_revision: PositiveInt = Field(...)


class CompletedData(TemporalModel):
    finished_at: UtcTimestamp = Field(...)
    checkpoint_revision: PositiveInt = Field(...)
    current_solution: PublicSolutionSlot = Field(...)
    previous_solution: PublicSolutionSlot = Field(...)


class StoppedData(TemporalModel):
    finished_at: UtcTimestamp = Field(...)
    error_code: Literal["CANCELLED_BY_USER"] = Field(...)
    checkpoint_revision: PositiveInt | None = Field(...)
    current_solution: PublicSolutionSlot = Field(...)
    previous_solution: PublicSolutionSlot = Field(...)


class InterruptedData(CompletedData):
    error_code: Annotated[str, StringConstraints(pattern=r"^[A-Z0-9_]{1,64}$")] = Field(...)


class FailedData(InterruptedData):
    retryable: bool = Field(...)


class EventBase(TemporalModel):
    assistant_contract_version: Literal["assistant-card-v3-draft-r1"] = Field(...)
    tourism_contract_version: Literal["tourism-api-v3-draft-r3"] = Field(...)
    event_version: Literal["assistant-event-v3-draft-r1"] = Field(...)
    thread_id: CanonicalUuid = Field(...)
    run_id: CanonicalUuid = Field(...)
    event_sequence: PositiveInt = Field(...)
    run_state: RunState = Field(...)
    emitted_at: UtcTimestamp = Field(...)


class RunStartedEvent(EventBase):
    type: Literal["run_started"] = Field(...)
    data: RunStartedData = Field(...)


class ProgressEvent(EventBase):
    type: Literal["progress"] = Field(...)
    data: ProgressData = Field(...)


class ToolSummaryEvent(EventBase):
    type: Literal["tool_summary"] = Field(...)
    data: ToolSummaryData = Field(...)


class CancelRequestedEvent(EventBase):
    type: Literal["cancel_requested"] = Field(...)
    data: CancelRequestedData = Field(...)


class CardReadyEvent(EventBase):
    type: Literal["card_ready"] = Field(...)
    data: CardReadyData = Field(...)


class CompletedEvent(EventBase):
    type: Literal["completed"] = Field(...)
    data: CompletedData = Field(...)


class StoppedEvent(EventBase):
    type: Literal["stopped"] = Field(...)
    data: StoppedData = Field(...)


class InterruptedEvent(EventBase):
    type: Literal["interrupted"] = Field(...)
    data: InterruptedData = Field(...)


class FailedEvent(EventBase):
    type: Literal["failed"] = Field(...)
    data: FailedData = Field(...)


EventVariant = Annotated[
    RunStartedEvent
    | ProgressEvent
    | ToolSummaryEvent
    | CancelRequestedEvent
    | CardReadyEvent
    | CompletedEvent
    | StoppedEvent
    | InterruptedEvent
    | FailedEvent,
    Field(discriminator="type"),
]


class AssistantEventV3(RootModel[EventVariant]):
    model_config = ConfigDict(strict=True)
