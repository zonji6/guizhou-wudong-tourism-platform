from typing import Any

import httpx

from app.config import get_settings


class TourismClient:
    """Legacy knowledge-only adapter; catalog reads use TourismV2Client."""

    def __init__(self) -> None:
        self.base_url = get_settings().tourism_service_url.rstrip("/")

    async def search_knowledge(self, keywords: str = "") -> list[dict[str, Any]]:
        async with httpx.AsyncClient(timeout=5) as client:
            response = await client.get(
                f"{self.base_url}/internal/agent/knowledge/search",
                params={"keywords": keywords},
            )
            response.raise_for_status()
            return response.json().get("data", [])


def service_card_item(item: dict[str, Any]) -> dict[str, Any]:
    """Project a validated v2 catalog item into the legacy display shape."""

    return {
        "title": item["name"],
        "summary": item["description"],
        "price": item["price"],
        "tags": item["tags"],
        "demoData": item["demoData"],
        "targetType": item["targetType"],
        "targetId": item["targetId"],
    }
