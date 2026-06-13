"""搜索相关 API"""

import redis.asyncio as aioredis
from fastapi import APIRouter, Depends, Query
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.redis_client import get_redis
from app.schemas.ad import FeedResponse
from app.services.search_service import SearchService

router = APIRouter(prefix="/ads", tags=["search"])


@router.get("/search", response_model=FeedResponse)
async def search_ads(
    q: str = Query(..., min_length=1, max_length=500, description="搜索关键词"),
    page: int = Query(default=1, ge=1, description="页码"),
    page_size: int = Query(default=20, ge=1, le=50, description="每页条数"),
    db: AsyncSession = Depends(get_db),
    redis: aioredis.Redis = Depends(get_redis),
) -> FeedResponse:
    """对话式搜索接口（RAG：意图识别 → 向量检索 → LLM Rerank）
    返回 FeedResponse 格式（items + next_cursor），与 Feed 接口保持一致。
    """
    search_service = SearchService(db, redis)
    return await search_service.search(q, page=page, page_size=page_size)
