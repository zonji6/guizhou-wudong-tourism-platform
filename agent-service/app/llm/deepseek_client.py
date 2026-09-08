import json
from typing import Any

from openai import AsyncOpenAI

from app.config import get_settings
from app.tools.tourism_client import service_card_item


class DeepSeekUnavailable(RuntimeError):
    pass


def get_deepseek_client() -> AsyncOpenAI:
    settings = get_settings()
    if not settings.deepseek_api_key:
        raise DeepSeekUnavailable("未配置 DEEPSEEK_API_KEY")
    return AsyncOpenAI(api_key=settings.deepseek_api_key, base_url="https://api.deepseek.com")


def sanitize_itinerary(payload: dict[str, Any], services: list[dict[str, Any]], retrieval_mode: str | None) -> dict[str, Any]:
    allowed = {str(item["id"]): item for item in services}
    days: list[dict[str, Any]] = []
    for raw_day in (payload.get("days") if isinstance(payload.get("days"), list) else [])[:3]:
        if not isinstance(raw_day, dict): continue
        items = []
        for raw_item in (raw_day.get("items") if isinstance(raw_day.get("items"), list) else [])[:6]:
            if not isinstance(raw_item, dict): continue
            item = {"time": str(raw_item.get("time", "")), "title": str(raw_item.get("title", "乌东行程节点")), "summary": str(raw_item.get("summary", "")), "demoData": True}
            service_id = str(raw_item.get("serviceId") or "")
            if service_id in allowed:
                item["serviceId"] = service_id; item["demoData"] = allowed[service_id].get("demoData", True)
            items.append(item)
        try: day_number = int(raw_day.get("day"))
        except (TypeError, ValueError): day_number = len(days) + 1
        days.append({"day": day_number, "theme": str(raw_day.get("theme") or "慢游乌东"), "items": items})
    result = {"days": days, "notice": "服务时间、价格与可预约情况请以服务方确认结果为准。"}
    if retrieval_mode: result["retrievalMode"] = retrieval_mode
    return result


async def compose_itinerary(user_text: str, messages: list[dict[str, str]], services: list[dict[str, Any]], sources: list[dict[str, Any]]) -> dict[str, Any]:
    prompt = {"request": user_text, "recentMessages": messages[-8:], "allowedServices": [service_card_item(item) for item in services], "sources": [{"title": item["title"], "content": item["content"]} for item in sources], "outputSchema": {"title": "string", "summary": "string", "days": [{"day": 1, "theme": "string", "items": [{"time": "string", "title": "string", "summary": "string", "serviceId": "allowed id or null"}]}]}}
    response = await get_deepseek_client().chat.completions.create(model=get_settings().deepseek_model, messages=[{"role": "system", "content": "只依据输入资料生成贵州乌东行程建议，以 json 输出；不得编造服务、库存、开放时段或价格。"}, {"role": "user", "content": json.dumps(prompt, ensure_ascii=False)}], response_format={"type": "json_object"}, max_tokens=1600)
    content = response.choices[0].message.content
    if not content or not content.strip(): raise DeepSeekUnavailable("DeepSeek 返回了空内容")
    try: parsed = json.loads(content)
    except (TypeError, json.JSONDecodeError) as exc: raise DeepSeekUnavailable("DeepSeek 返回了无效 JSON") from exc
    if not isinstance(parsed, dict): raise DeepSeekUnavailable("DeepSeek JSON 顶层不是对象")
    title, summary = str(parsed.get("title") or "").strip(), str(parsed.get("summary") or "").strip()
    data = sanitize_itinerary(parsed, services, next((item.get("retrieval_mode") for item in sources if item.get("retrieval_mode")), None))
    if not title or not summary or not data["days"] or not any(day["items"] for day in data["days"]): raise DeepSeekUnavailable("DeepSeek 行程结构不完整")
    return {"title": title, "summary": summary, "data": data}
