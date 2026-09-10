from __future__ import annotations

import json
from typing import Any

from openai import AsyncOpenAI

from app.config import get_settings
from app.tools.tourism_v3_contracts import KnowledgeEvidence
from app.v3_contracts import ConfirmedConditionsV3, ItineraryContent, ItineraryDay, ItineraryStop, PublicTarget


class DeepSeekUnavailable(RuntimeError):
    code = "MODEL_UNAVAILABLE"


def _has_configured_model() -> bool:
    return bool(get_settings().deepseek_api_key)


def _client() -> AsyncOpenAI:
    settings = get_settings()
    if not settings.deepseek_api_key:
        raise DeepSeekUnavailable("未配置 DeepSeek")
    return AsyncOpenAI(api_key=settings.deepseek_api_key, base_url=settings.deepseek_base_url)


async def _json_completion(system: str, payload: dict[str, object], *, max_tokens: int) -> dict[str, Any]:
    try:
        response = await _client().chat.completions.create(
            model=get_settings().deepseek_model,
            messages=[
                {"role": "system", "content": system},
                {"role": "user", "content": json.dumps(payload, ensure_ascii=False)},
            ],
            response_format={"type": "json_object"},
            max_tokens=max_tokens,
        )
        content = response.choices[0].message.content
        parsed = json.loads(content) if content else None
    except DeepSeekUnavailable:
        raise
    except Exception as exc:
        raise DeepSeekUnavailable("DeepSeek 调用失败") from exc
    if not isinstance(parsed, dict):
        raise DeepSeekUnavailable("DeepSeek 输出不是 JSON 对象")
    return parsed


async def answer_from_evidence(user_text: str, evidence: list[KnowledgeEvidence]) -> str:
    if not evidence:
        raise DeepSeekUnavailable("没有可用知识证据")
    if not _has_configured_model():
        excerpts = [f"《{item.title}》：{item.content.strip()}" for item in evidence[:3] if item.content.strip()]
        if not excerpts:
            raise DeepSeekUnavailable("没有可展示的知识证据")
        return "资料编排模式（未调用模型）：以下内容仅根据当前已发布资料整理。\n\n" + "\n\n".join(excerpts)
    payload = {
        "question": user_text,
        "evidence": [
            {
                "title": item.title,
                "content": item.content,
                "region": item.region,
                "periodText": item.period_text,
                "evidenceCategory": item.evidence_category,
                "usageLimitations": item.usage_limitations,
            }
            for item in evidence
        ],
        "outputSchema": {"answer": "string"},
    }
    result = await _json_completion(
        "只根据给定的贵州乌东当前生效资料回答。不得补造地方事实、联系方式、价格、开放时间或安全承诺；只输出 JSON。",
        payload,
        max_tokens=1200,
    )
    answer = str(result.get("answer") or "").strip()
    if not answer or len(answer) > 2400:
        raise DeepSeekUnavailable("DeepSeek 回答结构无效")
    return answer


async def compose_itinerary(
    user_text: str,
    conditions: ConfirmedConditionsV3,
    targets: list[PublicTarget],
    evidence: list[KnowledgeEvidence],
) -> ItineraryContent:
    allowed = {(item.target_type, item.target_id): item for item in targets}
    if not _has_configured_model():
        return _itinerary_from_published_material(conditions, targets, evidence)
    payload = {
        "request": user_text,
        "conditions": conditions.model_dump(mode="json", by_alias=True),
        "allowedTargets": [item.model_dump(mode="json", by_alias=True) for item in targets],
        "knowledge": [
            {"title": item.title, "content": item.content, "usageLimitations": item.usage_limitations}
            for item in evidence
        ],
        "outputSchema": {
            "title": "1-80 chars",
            "days": [
                {
                    "theme": "string or null",
                    "stops": [
                        {
                            "targetType": "allowed target type or null",
                            "targetId": "allowed target id or null",
                            "title": "string",
                            "note": "string or null",
                        }
                    ],
                }
            ],
        },
    }
    result = await _json_completion(
        "生成简洁的贵州乌东行程草案。结构化目标只能引用 allowedTargets；不确定目标时 targetType 与 targetId 都写 null。不得编造导航、容量、库存、班次或价格；只输出 JSON。",
        payload,
        max_tokens=1600,
    )
    days_raw = result.get("days")
    if not isinstance(days_raw, list):
        raise DeepSeekUnavailable("DeepSeek 行程结构无效")
    days: list[ItineraryDay] = []
    for day_index, raw_day in enumerate(days_raw[:30], 1):
        if not isinstance(raw_day, dict):
            continue
        raw_stops = raw_day.get("stops")
        stops: list[ItineraryStop] = []
        if isinstance(raw_stops, list):
            for stop_index, raw_stop in enumerate(raw_stops[:30], 1):
                if not isinstance(raw_stop, dict):
                    continue
                target_type = raw_stop.get("targetType")
                target_id = raw_stop.get("targetId")
                known = allowed.get((target_type, target_id))
                stops.append(
                    ItineraryStop(
                        sequence=stop_index,
                        target_type=known.target_type if known else None,
                        target_id=known.target_id if known else None,
                        title=(known.target_name if known else str(raw_stop.get("title") or "乌东慢游节点"))[:180],
                        note=(str(raw_stop["note"])[:500] if raw_stop.get("note") else None),
                    )
                )
        days.append(
            ItineraryDay(
                day=day_index,
                theme=(str(raw_day["theme"])[:80] if raw_day.get("theme") else None),
                stops=stops,
            )
        )
    title = str(result.get("title") or "乌东慢游行程")[:80].strip()
    if not title or not days:
        raise DeepSeekUnavailable("DeepSeek 行程结构不完整")
    return ItineraryContent(
        title=title,
        travel_date=conditions.travel_date,
        people_count=conditions.people_count,
        days=days,
    )


def _itinerary_from_published_material(
    conditions: ConfirmedConditionsV3,
    targets: list[PublicTarget],
    evidence: list[KnowledgeEvidence],
) -> ItineraryContent:
    """本机未配置模型时，只编排已发布地点和资料，不生成新地方事实。"""
    first_day = targets[:3]
    second_day = targets[3:6]

    def stops_for(items: list[PublicTarget], fallback_title: str) -> list[ItineraryStop]:
        stops = [
            ItineraryStop(
                sequence=index,
                target_type=item.target_type,
                target_id=item.target_id,
                title=item.target_name,
                note="已发布资料中的示意节点；开放状态、步行条件与实际路线请当天确认。",
            )
            for index, item in enumerate(items, 1)
        ]
        if stops:
            return stops
        source = evidence[0] if evidence else None
        return [
            ItineraryStop(
                sequence=1,
                target_type=None,
                target_id=None,
                title=fallback_title,
                note=("；".join(source.usage_limitations) if source else "暂无可核验的地点资料，请先查看公开资料后再安排。"),
            )
        ]

    days = [
        ItineraryDay(day=1, theme="沿溪读寨 · 资料示意", stops=stops_for(first_day, "阅读乌东已发布资料")),
        ItineraryDay(day=2, theme="茶与日常 · 资料示意", stops=stops_for(second_day, "继续查阅乌东茶旅资料")),
    ]
    return ItineraryContent(
        title="乌东资料编排 · 两日慢游（未调用模型）",
        travel_date=conditions.travel_date,
        people_count=conditions.people_count,
        days=days,
    )
