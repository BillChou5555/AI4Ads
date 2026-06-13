"""pytest 全局配置"""

import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

os.environ.setdefault("DATABASE_URL", "postgresql+asyncpg://aiads:aiads@localhost:5432/aiads_test")
os.environ.setdefault("REDIS_URL", "redis://localhost:6379/0")
os.environ.setdefault("LLM_API_KEY", "sk-test")
os.environ.setdefault("LLM_MODEL", "gpt-4o")
os.environ.setdefault("CELERY_BROKER_URL", "redis://localhost:6379/1")

import pytest_asyncio


@pytest_asyncio.fixture
async def test_db():
    """SQLite 内存数据库 session（每次测试独立）"""
    from tests.db_utils import create_test_tables, drop_test_tables, get_test_db

    await create_test_tables()
    async for session in get_test_db():
        yield session
    await drop_test_tables()


@pytest_asyncio.fixture
async def test_redis():
    """fakeredis 内存 Redis（每次测试独立）"""
    import fakeredis.aioredis as faioredis

    redis = faioredis.FakeRedis(decode_responses=True)
    yield redis
    await redis.flushall()
    await redis.aclose()
