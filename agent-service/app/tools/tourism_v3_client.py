from __future__ import annotations

from collections.abc import Sequence
from pathlib import Path
from typing import Any, TypeVar

import httpx
from pydantic import TypeAdapter, ValidationError

from app.config import get_settings
from app.tools.tourism_v3_contracts import (
    AnonymousInteractionRequest,
    AnonymousInteractionResult,
    ApiEnvelope,
    EligibilityCandidate,
    EligibilityRequest,
    EligibilityResult,
    ErrorEnvelope,
    FoodItem,
    KeywordSearchResult,
    MapPlacesResult,
    Product,
    RouteGuide,
    RunSummaryRequest,
    RunSummaryResult,
    StayProperty,
)
from app.v3_contracts import KnowledgeContext, KnowledgeDependency, TOURISM_CONTRACT_VERSION


T = TypeVar("T")


class TourismV3Error(RuntimeError):
    def __init__(self, code: str, *, status_code: int = 503) -> None:
        super().__init__(code)
        self.code = code
        self.status_code = status_code


def _read_secret(path: Path | None, unavailable_code: str) -> str:
    if path is None or not path.is_absolute() or not path.is_file():
        raise TourismV3Error(unavailable_code)
    value = path.read_text(encoding="utf-8").strip()
    if not value or "\n" in value:
        raise TourismV3Error(unavailable_code)
    return value


class TourismV3Client:
    def __init__(self) -> None:
        self.settings = get_settings()

    async def _request(
        self,
        method: str,
        path: str,
        response_type: Any,
        *,
        params: dict[str, str | int] | None = None,
        json_body: dict[str, object] | None = None,
        user_proof: str | None = None,
        anonymous_proof: str | None = None,
    ) -> T:
        credential = _read_secret(self.settings.ai_to_java_credential_file, "INTERNAL_AUTH_UNAVAILABLE")
        headers = {
            "X-Wudong-Contract": TOURISM_CONTRACT_VERSION,
            "X-Wudong-Service-Credential": credential,
        }
        if json_body is not None:
            headers["Content-Type"] = "application/json"
        if user_proof:
            headers["X-Wudong-User-Proof"] = f"Bearer {user_proof}"
        if anonymous_proof:
            headers["X-Wudong-Anonymous-Proof"] = anonymous_proof
        try:
            async with httpx.AsyncClient(
                base_url=self.settings.tourism_service_url,
                timeout=httpx.Timeout(15.0, connect=5.0),
                follow_redirects=False,
                trust_env=False,
            ) as client:
                response = await client.request(method, path, params=params, json=json_body, headers=headers)
        except httpx.HTTPError as exc:
            raise TourismV3Error("TOOL_UNAVAILABLE") from exc
        if response.is_redirect:
            raise TourismV3Error("TOOL_UNAVAILABLE")
        if response.headers.get("X-Wudong-Contract") != TOURISM_CONTRACT_VERSION:
            raise TourismV3Error("CONTRACT_INCOMPATIBLE", status_code=502)
        try:
            payload = response.json()
        except ValueError as exc:
            raise TourismV3Error("TOOL_UNAVAILABLE") from exc
        if not response.is_success:
            try:
                error = ErrorEnvelope.model_validate(payload)
            except ValidationError as exc:
                raise TourismV3Error("TOOL_UNAVAILABLE") from exc
            raise TourismV3Error(error.code, status_code=response.status_code)
        try:
            envelope = TypeAdapter(ApiEnvelope[response_type]).validate_python(payload, strict=True)
        except ValidationError as exc:
            raise TourismV3Error("CONTRACT_INCOMPATIBLE", status_code=502) from exc
        return envelope.data

    async def search_catalog(self, target_type: str, keywords: str, limit: int = 10) -> Sequence[object]:
        routes: dict[str, tuple[str, Any]] = {
            "PRODUCT": ("/internal/agent/products/search", list[Product]),
            "FOOD": ("/internal/agent/foods/search", list[FoodItem]),
            "STAY": ("/internal/agent/stays/search", list[StayProperty]),
            "PLACE": ("/internal/agent/places/search", MapPlacesResult),
            "ROUTE_GUIDE": ("/internal/agent/route-guides/search", list[RouteGuide]),
        }
        path, response_type = routes[target_type]
        result = await self._request(
            "GET",
            path,
            response_type,
            params={"keywords": keywords[:200] or "乌东", "limit": min(max(limit, 1), 10)},
        )
        return result.places if isinstance(result, MapPlacesResult) else result

    async def search_keyword_knowledge(self, keywords: str, limit: int = 10) -> KeywordSearchResult:
        return await self._request(
            "GET",
            "/internal/agent/knowledge/search",
            KeywordSearchResult,
            params={"keywords": keywords[:200] or "乌东", "limit": min(max(limit, 1), 10)},
        )

    async def verify_knowledge(
        self,
        context: KnowledgeContext,
        dependencies: list[KnowledgeDependency],
    ) -> EligibilityResult:
        request = EligibilityRequest(
            retrieval_mode=context.retrieval_mode,
            config_hash=context.config_hash,
            candidates=[
                EligibilityCandidate(document_id=item.document_id, build_id=item.build_id)
                for item in dependencies
            ],
        )
        return await self._request(
            "POST",
            "/internal/agent/knowledge/eligibility",
            EligibilityResult,
            json_body=request.model_dump(mode="json", by_alias=True),
        )

    async def accept_anonymous_interaction(
        self,
        thread_id: str,
        run_id: str,
        anonymous_proof: str,
    ) -> AnonymousInteractionResult:
        request = AnonymousInteractionRequest(thread_id=thread_id, run_id=run_id)
        return await self._request(
            "POST",
            "/internal/agent/anonymous/interactions",
            AnonymousInteractionResult,
            json_body=request.model_dump(mode="json", by_alias=True),
            anonymous_proof=anonymous_proof,
        )

    async def write_run_summary(self, request: RunSummaryRequest) -> RunSummaryResult:
        return await self._request(
            "POST",
            "/internal/agent/run-summaries",
            RunSummaryResult,
            json_body=request.model_dump(mode="json", by_alias=True),
        )
