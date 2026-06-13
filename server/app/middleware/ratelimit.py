"""基于 Redis 滑动窗口的限流中间件"""

import time

import redis.asyncio as aioredis
from fastapi import HTTPException, Request


class RateLimiter:
    """滑动窗口限流器"""

    def __init__(
        self,
        window: int = 1,  # 窗口大小（秒）
        max_requests: int = 10,  # 窗口内最大请求数
    ):
        self.window = window
        self.max_requests = max_requests

    async def check(self, redis: aioredis.Redis, key: str) -> bool:
        """检查是否允许请求，返回 True=允许，False=拒绝"""
        now = time.time()
        window_start = now - self.window

        pipe = redis.pipeline()
        pipe.zremrangebyscore(key, 0, window_start)
        pipe.zcard(key)
        pipe.zadd(key, {str(now): now})
        pipe.expire(key, self.window + 1)
        results = await pipe.execute()

        current_count = results[1]
        return current_count < self.max_requests


# 预设限流器
upload_limiter = RateLimiter(window=1, max_requests=10)   # 上传 10 req/s
feed_limiter = RateLimiter(window=1, max_requests=100)    # Feed 100 req/s
