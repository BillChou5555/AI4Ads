"""Celery Beat 定时任务：Feed 首页缓存预热"""

import asyncio
import json
import logging

from redis import Redis

from app.config import settings
from app.core.celery_app import celery_app
from app.core.database import async_session_factory
from app.schemas.ad import AdItem
from app.services.ad_service import AdService

logger = logging.getLogger(__name__)

SYNC_REDIS = Redis.from_url(settings.redis_url, decode_responses=True)

WARM_TABS = ["featured", "ecommerce", "local"]


@celery_app.task(
    name="app.tasks.feed_cache_warmer.warm_feed_cache",
)
def warm_feed_cache():
    """每 2 分钟预热各 Tab 首页缓存"""
    # 复用 ai_pipeline 模块的 loop（solo pool 单进程共享）
    from app.tasks.ai_pipeline import _LOOP
    try:
        _LOOP.run_until_complete(_warm_cache_async())
    except Exception as e:
        logger.error(f"Feed cache warmer failed: {e}")


async def _warm_cache_async():
    """查询各 Tab 首页数据，写入 Redis"""
    async with async_session_factory() as db:
        ad_service = AdService(db)

        for tab in WARM_TABS:
            ads = await ad_service.query_feed(tab=tab, cursor=None, size=20)
            items = [AdItem.model_validate(ad).model_dump(mode="json") for ad in ads]

            cache_key = f"feed:{tab}:home"
            if items:
                SYNC_REDIS.setex(
                    cache_key,
                    settings.feed_cache_ttl + 60,  # TTL 加 30-60s 随机偏移
                    json.dumps(items, default=str),
                )
                logger.info(f"Cache warmed: {cache_key} ({len(items)} items)")
            else:
                logger.info(f"No data for tab '{tab}', skipping cache warm")
