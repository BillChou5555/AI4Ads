"""应用配置，从环境变量读取"""

from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    database_url: str = "postgresql+asyncpg://aiads:aiads@localhost:5432/aiads"
    redis_url: str = "redis://localhost:6379/0"
    llm_api_key: str = "sk-xxx"
    llm_model: str = "gpt-4o"
    llm_base_url: str = "https://api.openai.com/v1"
    embedding_model: str = "BAAI/bge-large-zh-v1.5"
    embedding_dim: int = 1024
    embedding_device: str = "cpu"  # cpu 或 cuda
    celery_broker_url: str = "redis://localhost:6379/1"
    feed_cache_ttl: int = 300
    llm_request_timeout: int = 30

    model_config = {"env_file": ".env", "env_file_encoding": "utf-8"}


settings = Settings()
