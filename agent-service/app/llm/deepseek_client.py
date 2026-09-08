from openai import AsyncOpenAI

from app.config import get_settings


class DeepSeekUnavailable(RuntimeError):
    pass


def get_deepseek_client() -> AsyncOpenAI:
    settings = get_settings()
    if not settings.deepseek_api_key:
        raise DeepSeekUnavailable("未配置 DEEPSEEK_API_KEY，已使用本地确定性演示编排")
    return AsyncOpenAI(api_key=settings.deepseek_api_key, base_url="https://api.deepseek.com")
