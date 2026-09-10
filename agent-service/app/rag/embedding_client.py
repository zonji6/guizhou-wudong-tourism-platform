from app.config import get_settings


class EmbeddingUnavailable(RuntimeError):
    code = "VECTOR_CONFIGURATION_UNAVAILABLE"


class ConfiguredEmbeddingClient:
    """VECTOR capability gate; provider wiring is deliberately external and explicit."""

    async def embed(self, texts: list[str]) -> list[list[float]]:
        if not get_settings().vector_configured:
            raise EmbeddingUnavailable("Embedding 未配置；请显式使用新的 KEYWORD_DEMO run")
        raise EmbeddingUnavailable("VECTOR_BUILD_NOT_READY")


# Kept as an import-compatible alias for the dormant pre-v3 indexer only.
DashScopeEmbeddingClient = ConfiguredEmbeddingClient
