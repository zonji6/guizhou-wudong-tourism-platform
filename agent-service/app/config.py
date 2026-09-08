from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    tourism_service_url: str = "http://127.0.0.1:8080"
    deepseek_api_key: str | None = None
    deepseek_model: str = "deepseek-v4-flash"
    dashscope_api_key: str | None = None
    redis_url: str = "redis://127.0.0.1:6379/0"
    langsmith_tracing: bool = False
    langsmith_api_key: str | None = None

    model_config = SettingsConfigDict(env_file=".env", extra="ignore")


@lru_cache
def get_settings() -> Settings:
    return Settings()
