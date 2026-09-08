from typing import Any

from app.tools.tourism_client import TourismClient


QUERY_TERMS = ("苗族文化", "两天一夜", "长桌宴", "苗绣", "民宿", "茶旅", "茶园", "采茶", "制茶", "品茶", "乌东")


class KnowledgeRetriever:
    """配置未完成时明确降级，绝不以模型文本伪造检索结果。"""

    async def retrieve(self, query: str, top_k: int = 4) -> list[dict[str, Any]]:
        terms = [term for term in QUERY_TERMS if term in query][:4] or ["乌东"]
        documents_by_id: dict[str, dict[str, Any]] = {}
        client = TourismClient()
        for term in terms:
            for item in await client.search_knowledge(term):
                documents_by_id.setdefault(str(item["id"]), item)
        return [{"document_id": str(item["id"]), "title": item["title"], "content": item["content"], "retrieval_mode": "keyword_demo"} for item in list(documents_by_id.values())[:top_k]]
