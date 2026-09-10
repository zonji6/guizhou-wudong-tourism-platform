from decimal import Decimal
from typing import Annotated, Generic, Literal, Self, TypeVar

from pydantic import BeforeValidator, BaseModel, ConfigDict, Field, model_validator


def _to_camel(value: str) -> str:
    head, *tail = value.split("_")
    return head + "".join(part.capitalize() for part in tail)


def _reject_string_or_boolean_number(value: object) -> object:
    if isinstance(value, (str, bool)):
        raise ValueError("数值字段必须使用 JSON number")
    return value


class V2ResponseModel(BaseModel):
    """Strict Java response boundary; only original camelCase JSON is accepted."""

    model_config = ConfigDict(
        alias_generator=_to_camel,
        extra="forbid",
        frozen=True,
        populate_by_name=False,
        strict=True,
    )


CanonicalUuid = Annotated[
    str,
    Field(
        min_length=36,
        max_length=36,
        pattern=r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
    ),
]
Price = Annotated[
    Decimal,
    BeforeValidator(_reject_string_or_boolean_number),
    Field(gt=Decimal("0"), max_digits=10, decimal_places=2),
]
Coordinate = Annotated[
    Decimal,
    BeforeValidator(_reject_string_or_boolean_number),
    Field(max_digits=10, decimal_places=7),
]
CatalogStatus = Literal["PUBLISHED"]
MapStatus = Literal["AVAILABLE", "NOT_CONFIGURED", "DEGRADED"]
T = TypeVar("T")


class SuccessfulEnvelope(V2ResponseModel, Generic[T]):
    """Exact success envelope; non-200 responses never enter this model."""

    success: Literal[True]
    data: T
    message: None = None
    code: None = None


class FailedEnvelope(V2ResponseModel):
    """Strict failure shape for contract coverage; the client never trusts its body."""

    success: Literal[False]
    data: None = None
    message: str = Field(min_length=1)
    code: str | None = Field(default=None, min_length=1, max_length=64)


class ProductCatalogItem(V2ResponseModel):
    id: CanonicalUuid
    merchant_id: CanonicalUuid
    merchant_name: str = Field(min_length=1, max_length=120)
    name: str = Field(min_length=1, max_length=120)
    description: str = Field(min_length=1, max_length=1000)
    price: Price
    pickup_point: str = Field(min_length=1, max_length=160)
    tags: list[Annotated[str, Field(min_length=1, max_length=80)]]
    image_url: str | None = Field(default=None, min_length=1, max_length=500)
    demo_data: bool
    catalog_status: CatalogStatus


class FoodCatalogItem(V2ResponseModel):
    id: CanonicalUuid
    merchant_id: CanonicalUuid
    merchant_name: str = Field(min_length=1, max_length=120)
    name: str = Field(min_length=1, max_length=120)
    description: str = Field(min_length=1, max_length=1000)
    price: Price
    visit_time_text: str | None = Field(default=None, min_length=1, max_length=160)
    tags: list[Annotated[str, Field(min_length=1, max_length=80)]]
    image_url: str | None = Field(default=None, min_length=1, max_length=500)
    demo_data: bool
    catalog_status: CatalogStatus


class RoomTypeCatalogItem(V2ResponseModel):
    id: CanonicalUuid
    stay_property_id: CanonicalUuid
    stay_property_name: str = Field(min_length=1, max_length=120)
    name: str = Field(min_length=1, max_length=120)
    description: str = Field(min_length=1, max_length=1000)
    max_guests: int = Field(gt=0, le=2_147_483_647)
    price: Price
    image_url: str | None = Field(default=None, min_length=1, max_length=500)
    demo_data: bool
    catalog_status: CatalogStatus


class StayCatalogItem(V2ResponseModel):
    id: CanonicalUuid
    merchant_id: CanonicalUuid
    merchant_name: str = Field(min_length=1, max_length=120)
    name: str = Field(min_length=1, max_length=120)
    description: str = Field(min_length=1, max_length=1000)
    location_text: str | None = Field(default=None, min_length=1, max_length=160)
    tags: list[Annotated[str, Field(min_length=1, max_length=80)]]
    image_url: str | None = Field(default=None, min_length=1, max_length=500)
    demo_data: bool
    catalog_status: CatalogStatus
    room_types: list[RoomTypeCatalogItem]

    @model_validator(mode="after")
    def validate_room_parent(self) -> Self:
        if any(
            room.stay_property_id != self.id or room.stay_property_name != self.name
            for room in self.room_types
        ):
            raise ValueError("住宿房型与住宿主体不一致")
        return self


class PlaceCatalogItem(V2ResponseModel):
    id: CanonicalUuid
    name: str = Field(min_length=1, max_length=120)
    category: str = Field(min_length=1, max_length=64)
    description: str = Field(min_length=1, max_length=1000)
    latitude: Coordinate | None = None
    longitude: Coordinate | None = None
    tags: list[Annotated[str, Field(min_length=1, max_length=80)]]
    image_url: str | None = Field(default=None, min_length=1, max_length=500)
    demo_data: bool
    catalog_status: CatalogStatus

    @model_validator(mode="after")
    def validate_coordinates(self) -> Self:
        if (self.latitude is None) != (self.longitude is None):
            raise ValueError("地点经纬度必须同时提供或同时为空")
        if self.latitude is not None and not Decimal("-90") <= self.latitude <= Decimal("90"):
            raise ValueError("地点纬度超出范围")
        if self.longitude is not None and not Decimal("-180") <= self.longitude <= Decimal("180"):
            raise ValueError("地点经度超出范围")
        return self


class MapPlacesResult(V2ResponseModel):
    map_status: MapStatus
    places: list[PlaceCatalogItem]
    message: str | None = Field(default=None, min_length=1, max_length=300)

    @model_validator(mode="after")
    def validate_map_state(self) -> Self:
        if self.map_status != "AVAILABLE" and self.places:
            raise ValueError("地图不可用时不得返回地点")
        if self.map_status != "AVAILABLE" and not self.message:
            raise ValueError("地图不可用时必须提供说明")
        if (
            self.map_status == "NOT_CONFIGURED"
            and self.message != "地图服务或已核验坐标尚未配置"
        ):
            raise ValueError("地图未配置提示与冻结契约不一致")
        if self.map_status == "DEGRADED" and (
            self.message is None or "不可作为实时导航" not in self.message
        ):
            raise ValueError("地图降级提示必须说明不可作为实时导航")
        if self.map_status == "AVAILABLE" and any(
            place.latitude is None or place.longitude is None for place in self.places
        ):
            raise ValueError("可用地图中的地点必须包含完整坐标")
        return self


ProductSearchEnvelope = SuccessfulEnvelope[list[ProductCatalogItem]]
FoodSearchEnvelope = SuccessfulEnvelope[list[FoodCatalogItem]]
StaySearchEnvelope = SuccessfulEnvelope[list[StayCatalogItem]]
PlaceSearchEnvelope = SuccessfulEnvelope[MapPlacesResult]


class KnowledgeView(V2ResponseModel):
    id: CanonicalUuid
    title: str = Field(min_length=1, max_length=180)
    content: str = Field(min_length=1, max_length=2400)
    tags: list[Annotated[str, Field(min_length=1, max_length=80)]]
    demo_data: bool


class RouteNodeView(V2ResponseModel):
    sequence: int = Field(gt=0)
    place_id: CanonicalUuid | None = None
    place_name: str = Field(min_length=1, max_length=180)
    note: str = Field(min_length=1, max_length=500)


class RouteGuideView(V2ResponseModel):
    id: CanonicalUuid
    title: str = Field(min_length=1, max_length=180)
    content: str = Field(min_length=1, max_length=2400)
    tags: list[Annotated[str, Field(min_length=1, max_length=80)]]
    route_summary: str = Field(min_length=1, max_length=300)
    route_nodes: list[RouteNodeView]
    demo_data: bool


KnowledgeSearchEnvelope = SuccessfulEnvelope[list[KnowledgeView]]
RouteGuideSearchEnvelope = SuccessfulEnvelope[list[RouteGuideView]]
