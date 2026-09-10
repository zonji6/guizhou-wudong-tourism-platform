from __future__ import annotations

from typing import Annotated, Generic, Literal, TypeVar

from pydantic import Field, StringConstraints

from app.v3_contracts import (
    CanonicalUuid,
    DemoPrice,
    KnowledgeDependency,
    KnowledgeReference,
    ReferencePrice,
    Sha256Digest,
    StrictModel,
    UtcTimestamp,
)


T = TypeVar("T")


class ApiEnvelope(StrictModel, Generic[T]):
    success: Literal[True] = Field(...)
    data: T = Field(...)
    message: None = Field(...)
    code: None = Field(...)
    details: None = Field(...)


class ErrorEnvelope(StrictModel):
    success: Literal[False] = Field(...)
    data: None = Field(...)
    message: str = Field(..., min_length=1, max_length=200)
    code: Annotated[str, StringConstraints(pattern=r"^[A-Z0-9_]{1,64}$")] = Field(...)
    details: dict[str, object] | None = Field(...)


class CatalogBase(StrictModel):
    id: CanonicalUuid = Field(...)
    name: str = Field(..., min_length=1, max_length=180)
    description: str = Field(..., max_length=1000)
    demo_data: bool = Field(...)
    verification_status: Literal["UNVERIFIED", "VERIFIED"] = Field(...)
    catalog_status: Literal["PUBLISHED"] = Field(...)
    version: Annotated[int, Field(strict=True, gt=0)] = Field(...)


class Product(CatalogBase):
    merchant_id: CanonicalUuid = Field(...)
    merchant_name: str = Field(..., min_length=1, max_length=180)
    reference_price: ReferencePrice | None = Field(...)
    demo_price: DemoPrice | None = Field(...)
    pickup_point: str = Field(..., max_length=300)
    tags: list[str] = Field(..., max_length=20)
    image_url: str | None = Field(...)
    orderable: bool = Field(...)


class FoodItem(CatalogBase):
    merchant_id: CanonicalUuid = Field(...)
    merchant_name: str = Field(..., min_length=1, max_length=180)
    item_type: Literal["DISH", "DRINK", "SET"] = Field(...)
    reference_price: ReferencePrice | None = Field(...)
    demo_price: DemoPrice | None = Field(...)
    visit_time_text: str | None = Field(..., max_length=180)
    tags: list[str] = Field(..., max_length=20)
    image_url: str | None = Field(...)
    orderable: bool = Field(...)


class RoomType(CatalogBase):
    stay_property_id: CanonicalUuid = Field(...)
    stay_property_name: str = Field(..., min_length=1, max_length=180)
    max_guests_per_room: Annotated[int, Field(strict=True, gt=0)] = Field(...)
    reference_price: ReferencePrice | None = Field(...)
    demo_price: DemoPrice | None = Field(...)
    image_url: str | None = Field(...)
    orderable: bool = Field(...)


class StayProperty(CatalogBase):
    merchant_id: CanonicalUuid = Field(...)
    merchant_name: str = Field(..., min_length=1, max_length=180)
    location_text: str | None = Field(..., max_length=300)
    tags: list[str] = Field(..., max_length=20)
    image_url: str | None = Field(...)
    room_types: list[RoomType] = Field(..., max_length=50)


Coordinate = Annotated[str, StringConstraints(pattern=r"^(?:0\.\d{4}|1\.0000)$")]


class SchematicPosition(StrictModel):
    x: Coordinate = Field(...)
    y: Coordinate = Field(...)


class Place(CatalogBase):
    category: str = Field(..., min_length=1, max_length=80)
    tags: list[str] = Field(..., max_length=20)
    image_url: str | None = Field(...)
    schematic_position: SchematicPosition | None = Field(...)


class MapPlacesResult(StrictModel):
    map_mode: Literal["SCHEMATIC"] = Field(...)
    navigation_available: Literal[False] = Field(...)
    scale: Literal["NOT_TO_SCALE"] = Field(...)
    notice: str = Field(..., min_length=1, max_length=300)
    places: list[Place] = Field(..., max_length=100)


class RouteNode(StrictModel):
    sequence: Annotated[int, Field(strict=True, gt=0)] = Field(...)
    place_id: CanonicalUuid | None = Field(...)
    place_name: str = Field(..., min_length=1, max_length=180)
    note: str | None = Field(..., max_length=300)
    drawable: bool = Field(...)


class RouteGuide(StrictModel):
    id: CanonicalUuid = Field(...)
    post_type: Literal["ROUTE_GUIDE"] = Field(...)
    title: str = Field(..., min_length=1, max_length=80)
    content: str = Field(..., min_length=1, max_length=2000)
    author_name: str = Field(..., min_length=1, max_length=80)
    cover_url: str | None = Field(...)
    tags: list[str] = Field(..., max_length=5)
    route_summary: str = Field(..., min_length=1, max_length=300)
    route_nodes: list[RouteNode] = Field(..., min_length=1, max_length=12)
    demo_data: bool = Field(...)
    legacy_data: bool = Field(...)
    published_at: UtcTimestamp = Field(...)


class KnowledgeEvidence(StrictModel):
    document_id: CanonicalUuid = Field(...)
    build_id: CanonicalUuid = Field(...)
    snapshot_hash: Sha256Digest = Field(...)
    title: str = Field(..., min_length=1, max_length=180)
    content: str = Field(..., min_length=1, max_length=20000)
    tags: list[str] = Field(..., max_length=50)
    region: str | None = Field(..., max_length=120)
    period_text: str | None = Field(..., max_length=180)
    evidence_category: str = Field(..., min_length=1, max_length=120)
    usage_limitations: list[str] = Field(..., max_length=20)
    demo_data: bool = Field(...)
    references: list[KnowledgeReference] = Field(..., max_length=50)


class KeywordSearchItem(StrictModel):
    rank: Annotated[int, Field(strict=True, gt=0)] = Field(...)
    document_id: CanonicalUuid = Field(...)
    build_id: CanonicalUuid = Field(...)
    evidence: KnowledgeEvidence = Field(...)


class KeywordSearchResult(StrictModel):
    retrieval_mode: Literal["KEYWORD_DEMO"] = Field(...)
    config_hash: Sha256Digest = Field(...)
    checked_at: UtcTimestamp = Field(...)
    results: list[KeywordSearchItem] = Field(..., max_length=10)


class EligibilityCandidate(StrictModel):
    document_id: CanonicalUuid = Field(...)
    build_id: CanonicalUuid = Field(...)


class EligibilityRequest(StrictModel):
    retrieval_mode: Literal["KEYWORD_DEMO", "VECTOR"] = Field(...)
    config_hash: Sha256Digest = Field(...)
    candidates: list[EligibilityCandidate] = Field(..., min_length=1, max_length=50)


class EligibilityItem(StrictModel):
    document_id: CanonicalUuid = Field(...)
    build_id: CanonicalUuid = Field(...)
    eligible: bool = Field(...)
    evidence: KnowledgeEvidence | None = Field(default=None)


class EligibilityResult(StrictModel):
    retrieval_mode: Literal["KEYWORD_DEMO", "VECTOR"] = Field(...)
    config_hash: Sha256Digest = Field(...)
    checked_at: UtcTimestamp = Field(...)
    results: list[EligibilityItem] = Field(..., min_length=1, max_length=50)


class AnonymousInteractionRequest(StrictModel):
    thread_id: CanonicalUuid = Field(...)
    run_id: CanonicalUuid = Field(...)


class AnonymousInteractionResult(StrictModel):
    thread_id: CanonicalUuid = Field(...)
    run_id: CanonicalUuid = Field(...)
    last_interaction_at: UtcTimestamp = Field(...)
    session_expires_at: UtcTimestamp = Field(...)
    replayed: bool = Field(...)


class RunSummaryRequest(StrictModel):
    thread_id: CanonicalUuid = Field(...)
    run_id: CanonicalUuid = Field(...)
    summary_sequence: Annotated[int, Field(strict=True, gt=0)] = Field(...)
    run_state: Literal["RUNNING", "CANCEL_REQUESTED", "STOPPED", "INTERRUPTED", "COMPLETED", "FAILED"] = Field(...)
    agent_type: Literal["KNOWLEDGE_GUIDE", "SERVICE_RECOMMENDER", "ITINERARY_PLANNER"] = Field(...)
    retrieval_mode: Literal["NONE", "KEYWORD_DEMO", "VECTOR"] = Field(...)
    started_at: UtcTimestamp = Field(...)
    finished_at: UtcTimestamp | None = Field(...)
    knowledge_dependencies: list[KnowledgeDependency] = Field(..., max_length=50)
    candidate_refs: list[dict[str, object]] = Field(..., max_length=20)
    error_code: str | None = Field(...)


class RunSummaryResult(StrictModel):
    thread_id: CanonicalUuid = Field(...)
    run_id: CanonicalUuid = Field(...)
    received_sequence: Annotated[int, Field(strict=True, gt=0)] = Field(...)
    stored_sequence: Annotated[int, Field(strict=True, gt=0)] = Field(...)
    stored_state: Literal["RUNNING", "CANCEL_REQUESTED", "STOPPED", "INTERRUPTED", "COMPLETED", "FAILED"] = Field(...)
    outcome: Literal["APPLIED", "REPLAYED", "STALE_IGNORED"] = Field(...)
    received_at: UtcTimestamp = Field(...)
