from typing import Any

from app.config import get_settings


class KnowledgeRetriever:
    """配置未完成时明确降级，绝不以模型文本伪造检索结果。"""

    async def retrieve(self, query: str, top_k: int = 4) -> list[dict[str, Any]]:
        if not get_settings().dashscope_api_key:
            return []
        # Redis Stack 向量检索的索引名固定为 idx:udong:knowledge；待用户配置云端 Embedding 后接入。
        return []
