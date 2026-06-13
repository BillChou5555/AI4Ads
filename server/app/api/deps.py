"""API 依赖注入"""

from typing import AsyncGenerator

import redis.asyncio as aioredis
from fastapi import Request
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.redis_client import get_redis


async def get_device_id(request: Request) -> str:
    """从请求 Header 提取 device_id，兼容 X-Device-ID 和 device_id"""
    return request.headers.get("X-Device-ID") or request.headers.get("device_id", "")
