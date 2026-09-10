from functools import lru_cache
from pathlib import Path
from typing import Literal
from urllib.parse import urlparse

from pydantic import Field, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    tourism_service_url: str = "http://127.0.0.1:8080"
    deepseek_api_key: str | None = None
    deepseek_model: str = "deepseek-v4-flash"
    deepseek_base_url: str = "https://api.deepseek.com"
    redis_url: str = "redis://127.0.0.1:6379/0"
    public_web_origin: str = "http://127.0.0.1:5174"

    jwt_issuer: Literal["wudong-java-local"] = "wudong-java-local"
    jwt_key_id: str | None = None
    jwt_public_key_file: Path | None = None
    java_to_ai_credential_file: Path | None = None
    ai_to_java_credential_file: Path | None = None

    ws_auth_timeout_seconds: int = Field(default=10, ge=1, le=30)
    auth_recheck_seconds: int = Field(default=5, ge=1, le=30)
    ai_run_deadline_seconds: int = Field(default=180, ge=1, le=600)
    ai_cancel_wait_seconds: int = Field(default=10, ge=1, le=30)
    ai_rate_window_seconds: int = Field(default=60, ge=1)
    ai_rate_requests: int = Field(default=6, ge=1, le=100)
    ai_global_concurrency: int = Field(default=3, ge=1, le=16)

    knowledge_retrieval_mode: Literal["KEYWORD_DEMO", "VECTOR"] = "KEYWORD_DEMO"
    knowledge_search_max_results: int = Field(default=10, ge=1, le=10)
    embedding_provider: str | None = None
    embedding_model: str | None = None
    embedding_vector_dimension: int | None = Field(default=None, ge=1, le=4096)
    embedding_endpoint: str | None = None
    embedding_credential_file: Path | None = None

    langsmith_tracing: bool = False
    langsmith_api_key: str | None = None

    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    @model_validator(mode="after")
    def validate_local_boundaries(self) -> "Settings":
        parsed = urlparse(self.tourism_service_url)
        if parsed.scheme not in {"http", "https"} or parsed.hostname not in {"127.0.0.1", "localhost"}:
            raise ValueError("TOURISM_SERVICE_URL 必须指向本机回环地址")
        if self.embedding_endpoint:
            embedding = urlparse(self.embedding_endpoint)
            if embedding.scheme != "https" or not embedding.hostname:
                raise ValueError("EMBEDDING_ENDPOINT 必须是绝对 HTTPS 地址")
        return self

    @property
    def vector_configured(self) -> bool:
        return all(
            (
                self.embedding_provider,
                self.embedding_model,
                self.embedding_vector_dimension,
                self.embedding_endpoint,
                self.embedding_credential_file,
            )
        )


@lru_cache
def get_settings() -> Settings:
    return Settings()
