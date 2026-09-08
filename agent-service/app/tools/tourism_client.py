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

    async def create_pending_booking(
        self, service_id: str, travel_date: str | None, people: int | None, thread_id: str
    ) -> dict[str, Any]:
        async with httpx.AsyncClient(timeout=5) as client:
            response = await client.post(
                f"{self.base_url}/internal/agent/pending-bookings",
                json={
                    "serviceId": service_id,
                    "travelDate": travel_date,
                    "peopleCount": people or 1,
                    "contactName": "演示游客",
                    "contactPhone": "13800000000",
                    "note": "来自 AI 规划卡片",
                    "threadId": thread_id,
                },
            )
            response.raise_for_status()
            return response.json().get("data", {})
