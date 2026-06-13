"""Redis 异步客户端封装"""

import redis.asyncio as aioredis
from app.config import settings

redis_client: aioredis.Redis = aioredis.from_url(
    settings.redis_url,
    encoding="utf-8",
    decode_responses=True,
)


async def get_redis() -> aioredis.Redis:
    return redis_client
