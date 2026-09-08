from app.config import get_settings


class EmbeddingUnavailable(RuntimeError):
    pass


class DashScopeEmbeddingClient:
    """预留百炼 text-embedding-v4（1024 维）调用边界。"""

    model = "text-embedding-v4"
    dimensions = 1024

    async def embed(self, texts: list[str]) -> list[list[float]]:
        if not get_settings().dashscope_api_key:
            raise EmbeddingUnavailable("未配置 DASHSCOPE_API_KEY，知识库向量检索暂不可用")
        raise EmbeddingUnavailable("云端 Embedding 服务待配置后启用")
