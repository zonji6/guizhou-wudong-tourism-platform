import math
import re
from collections.abc import Sequence
from typing import Literal, TypeVar
from urllib.parse import urlsplit

import httpx
from pydantic import BaseModel, TypeAdapter, ValidationError

from app.config import get_settings
from app.tools.tourism_v2_contracts import (
    FoodCatalogItem,
    FoodSearchEnvelope,
    KnowledgeSearchEnvelope,
    KnowledgeView,
    MapPlacesResult,
    PlaceSearchEnvelope,
    ProductCatalogItem,
    ProductSearchEnvelope,
    StayCatalogItem,
    StaySearchEnvelope,
    RouteGuideSearchEnvelope,
    RouteGuideView,
)


_CANONICAL_UUID = re.compile(
    r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
)
_LOOPBACK_HOSTS = frozenset({"127.0.0.1", "localhost", "::1"})
_INTERNAL_CATALOG_PATHS = frozenset(
    {
        "/internal/agent/products/search",
        "/internal/agent/foods/search",
        "/internal/agent/stays/search",
        "/internal/agent/places/search",
        "/internal/agent/knowledge/search",
        "/internal/agent/route-guides/search",
    }
)
_PRODUCT_ADAPTER = TypeAdapter(ProductSearchEnvelope)
_FOOD_ADAPTER = TypeAdapter(FoodSearchEnvelope)
_STAY_ADAPTER = TypeAdapter(StaySearchEnvelope)
_PLACE_ADAPTER = TypeAdapter(PlaceSearchEnvelope)
_KNOWLEDGE_ADAPTER = TypeAdapter(KnowledgeSearchEnvelope)
_ROUTE_GUIDE_ADAPTER = TypeAdapter(RouteGuideSearchEnvelope)

InternalCatalogPath = Literal[
    "/internal/agent/products/search",
    "/internal/agent/foods/search",
    "/internal/agent/stays/search",
    "/internal/agent/places/search",
    "/internal/agent/knowledge/search",
    "/internal/agent/route-guides/search",
]
TourismV2ErrorCode = Literal[
    "TOURISM_CONFIGURATION_INVALID",
    "TOURISM_QUERY_INVALID",
    "TOURISM_SERVICE_UNAVAILABLE",
    "TOURISM_SERVICE_REJECTED",
    "TOURISM_RESPONSE_INVALID",
]
EnvelopeT = TypeVar("EnvelopeT", bound=BaseModel)


class TourismV2ClientError(RuntimeError):
    """Safe cross-service failure without URLs, bodies, headers, or stacks."""

    def __init__(
        self,
        code: TourismV2ErrorCode,
        public_message: str,
        *,
        retryable: bool,
    ) -> None:
        super().__init__(public_message)
        self.code = code
        self.public_message = public_message
        self.retryable = retryable


class TourismV2Client:
    """Read-only client for Java's currently implemented v2 catalog routes."""

    def __init__(self, base_url: str | None = None, timeout_seconds: float = 5.0) -> None:
        configured_url = base_url if base_url is not None else get_settings().tourism_service_url
        self._base_url = _validated_loopback_base_url(configured_url)
        self._timeout_seconds = _validated_timeout(timeout_seconds)

    async def search_products(
        self,
        *,
        keywords: str | None = None,
        tags: Sequence[str] | None = None,
    ) -> list[ProductCatalogItem]:
        envelope = await self._get(
            "/internal/agent/products/search",
            _query_parameters(keywords=keywords, tags=tags),
            _PRODUCT_ADAPTER,
        )
        return envelope.data

    async def search_foods(
        self,
        *,
        keywords: str | None = None,
        tags: Sequence[str] | None = None,
    ) -> list[FoodCatalogItem]:
        envelope = await self._get(
            "/internal/agent/foods/search",
            _query_parameters(keywords=keywords, tags=tags),
            _FOOD_ADAPTER,
        )
        return envelope.data

    async def search_stays(
        self,
        *,
        keywords: str | None = None,
        tags: Sequence[str] | None = None,
        people_count: int | None = None,
        room_type_id: str | None = None,
    ) -> list[StayCatalogItem]:
        parameters = _query_parameters(keywords=keywords, tags=tags)
        if people_count is not None:
            if (
                isinstance(people_count, bool)
                or not isinstance(people_count, int)
                or people_count <= 0
                or people_count > 2_147_483_647
            ):
                raise _query_error("检索人数必须是正整数。")
            parameters.append(("peopleCount", str(people_count)))
        if room_type_id is not None:
            parameters.append(("roomTypeId", _canonical_uuid(room_type_id)))
        envelope = await self._get(
            "/internal/agent/stays/search",
            parameters,
            _STAY_ADAPTER,
        )
        return envelope.data

    async def search_places(
        self,
        *,
        keywords: str | None = None,
        tags: Sequence[str] | None = None,
    ) -> MapPlacesResult:
        envelope = await self._get(
            "/internal/agent/places/search",
            _query_parameters(keywords=keywords, tags=tags),
            _PLACE_ADAPTER,
        )
        return envelope.data

    async def search_knowledge(self, *, keywords: str | None = None) -> list[KnowledgeView]:
        envelope = await self._get(
            "/internal/agent/knowledge/search", _query_parameters(keywords=keywords, tags=None), _KNOWLEDGE_ADAPTER
        )
        return envelope.data

    async def search_route_guides(self, *, keywords: str | None = None) -> list[RouteGuideView]:
        envelope = await self._get(
            "/internal/agent/route-guides/search", _query_parameters(keywords=keywords, tags=None), _ROUTE_GUIDE_ADAPTER
        )
        return envelope.data

    async def _get(
        self,
        path: InternalCatalogPath,
        parameters: list[tuple[str, str]],
        adapter: TypeAdapter[EnvelopeT],
    ) -> EnvelopeT:
        if path not in _INTERNAL_CATALOG_PATHS:
            raise TourismV2ClientError(
                "TOURISM_CONFIGURATION_INVALID",
                "旅游业务服务内部路由配置无效。",
                retryable=False,
            )
        try:
            async with httpx.AsyncClient(
                timeout=self._timeout_seconds,
                follow_redirects=False,
                trust_env=False,
            ) as client:
                response = await client.get(f"{self._base_url}{path}", params=parameters)
        except (httpx.TimeoutException, httpx.RequestError):
            raise TourismV2ClientError(
                "TOURISM_SERVICE_UNAVAILABLE",
                "旅游业务服务暂时无法连接，请稍后重试。",
                retryable=True,
            ) from None

        if response.status_code != 200:
            raise _http_error(response.status_code)

        try:
            return adapter.validate_json(response.content)
        except (ValidationError, ValueError):
            raise TourismV2ClientError(
                "TOURISM_RESPONSE_INVALID",
                "旅游业务服务返回了无法识别的目录结果。",
                retryable=False,
            ) from None


def _validated_loopback_base_url(value: object) -> str:
    if not isinstance(value, str) or not value or value != value.strip():
        raise _configuration_error()
    try:
        parsed = urlsplit(value)
        port = parsed.port
    except ValueError:
        raise _configuration_error() from None
    if (
        parsed.scheme != "http"
        or parsed.hostname not in _LOOPBACK_HOSTS
        or parsed.username is not None
        or parsed.password is not None
        or parsed.query
        or parsed.fragment
        or parsed.path not in ("", "/")
    ):
        raise _configuration_error()
    if port is not None and not 1 <= port <= 65_535:
        raise _configuration_error()
    return value.rstrip("/")


def _validated_timeout(value: object) -> float:
    if (
        isinstance(value, bool)
        or not isinstance(value, (int, float))
        or not math.isfinite(value)
        or value <= 0
    ):
        raise _configuration_error()
    return float(value)


def _query_parameters(
    *,
    keywords: str | None,
    tags: Sequence[str] | None,
) -> list[tuple[str, str]]:
    parameters: list[tuple[str, str]] = []
    if keywords is not None:
        if (
            not isinstance(keywords, str)
            or not keywords
            or keywords != keywords.strip()
            or len(keywords) > 120
        ):
            raise _query_error("检索关键词格式无效。")
        parameters.append(("keywords", keywords))
    if tags is None:
        return parameters
    if not isinstance(tags, Sequence) or isinstance(tags, (str, bytes)):
        raise _query_error("检索标签必须是字符串列表。")

    seen: set[str] = set()
    for tag in tags:
        if (
            not isinstance(tag, str)
            or not tag
            or tag != tag.strip()
            or len(tag) > 80
            or "," in tag
            or tag in seen
        ):
            raise _query_error("检索标签格式无效或重复。")
        seen.add(tag)
        parameters.append(("tag", tag))
    return parameters


def _canonical_uuid(value: object) -> str:
    if not isinstance(value, str):
        raise _query_error("房型标识格式无效。")
    normalized = value.lower()
    if _CANONICAL_UUID.fullmatch(normalized) is None:
        raise _query_error("房型标识格式无效。")
    return normalized


def _configuration_error() -> TourismV2ClientError:
    return TourismV2ClientError(
        "TOURISM_CONFIGURATION_INVALID",
        "旅游业务服务仅允许配置为本机 HTTP 地址。",
        retryable=False,
    )


def _query_error(message: str) -> TourismV2ClientError:
    return TourismV2ClientError(
        "TOURISM_QUERY_INVALID",
        message,
        retryable=False,
    )


def _http_error(status_code: int) -> TourismV2ClientError:
    if status_code == 429 or status_code >= 500:
        return TourismV2ClientError(
            "TOURISM_SERVICE_UNAVAILABLE",
            "旅游业务服务暂时无法完成检索，请稍后重试。",
            retryable=True,
        )
    return TourismV2ClientError(
        "TOURISM_SERVICE_REJECTED",
        "旅游业务服务拒绝了本次内部检索。",
        retryable=False,
    )
