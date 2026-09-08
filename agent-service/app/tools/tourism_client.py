from typing import Any

import httpx

from app.config import get_settings


class TourismClient:
    """AI 服务不直连 MySQL；所有业务读写都经由 Java 内部接口。"""

    def __init__(self) -> None:
        self.base_url = get_settings().tourism_service_url.rstrip("/")

    async def search_services(self, keywords: str = "", tags: str = "") -> list[dict[str, Any]]:
        async with httpx.AsyncClient(timeout=5) as client:
            response = await client.get(
                f"{self.base_url}/internal/agent/services/search",
                params={"keywords": keywords, "tags": tags},
            )
            response.raise_for_status()
            return response.json().get("data", [])

    async def search_knowledge(self, keywords: str = "") -> list[dict[str, Any]]:
        async with httpx.AsyncClient(timeout=5) as client:
            response = await client.get(f"{self.base_url}/internal/agent/knowledge/search", params={"keywords": keywords})
            response.raise_for_status()
            return response.json().get("data", [])

    async def create_pending_booking(
        self, service_id: str, travel_date: str, people: int, contact_name: str, contact_phone: str, thread_id: str
    ) -> dict[str, Any]:
        async with httpx.AsyncClient(timeout=5) as client:
            response = await client.post(
                f"{self.base_url}/internal/agent/pending-bookings",
                json={
                    "serviceId": service_id,
                    "travelDate": travel_date,
                    "peopleCount": people,
                    "contactName": contact_name,
                    "contactPhone": contact_phone,
                    "note": "来自乌东向导的待确认预约",
                    "threadId": thread_id,
                },
            )
            response.raise_for_status()
            return response.json().get("data", {})


def service_card_item(item: dict[str, Any]) -> dict[str, Any]:
    return {"serviceId": str(item["id"]), "title": item.get("name", "乌东体验"), "summary": item.get("description", ""), "price": item.get("price"), "demoData": item.get("demoData", True)}
