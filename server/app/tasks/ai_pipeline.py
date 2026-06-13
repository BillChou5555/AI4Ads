"""AI 异步管线：摘要+标签生成、标签去重、Embedding 入库"""

import asyncio
import json
import logging

from celery import Task
from sqlalchemy import select

from app.config import settings
from app.core.celery_app import celery_app
from app.core.database import async_session_factory, engine
from app.models.ad import Ad
from app.services.embedding_service import EmbeddingService
from app.services.llm_service import llm_client
from app.services.vector_store import VectorStore

logger = logging.getLogger(__name__)

# 模块级 event loop，solo pool 下复用，避免 asyncio.run() 关闭 loop 导致连接跨 loop 错误
_LOOP = asyncio.new_event_loop()
asyncio.set_event_loop(_LOOP)


class AIAdsTask(Task):
    """带默认重试配置的 Celery 基类"""
    max_retries = 3
    default_retry_delay = 10


@celery_app.task(
    bind=True,
    base=AIAdsTask,
    name="app.tasks.ai_pipeline.process_ad_summary",
)
def process_ad_summary(self, ad_id: str):
    """AI 摘要+标签生成 + 标签去重 + Embedding 入库"""
    try:
        _LOOP.run_until_complete(_process_ad_summary_async(ad_id))
    except Exception as e:
        logger.error(f"AI pipeline failed for ad {ad_id}: {e}")
        if self.request.retries < self.max_retries:
            raise self.retry(exc=e)


async def _process_ad_summary_async(ad_id: str):
    """AI 管线异步主流程"""
    async with async_session_factory() as db:
        # 1. 查询广告
        result = await db.execute(select(Ad).where(Ad.id == ad_id))
        ad = result.scalar_one_or_none()
        if not ad:
            logger.warning(f"Ad {ad_id} not found, skip AI pipeline")
            return

        # 2. 调用 LLM 生成摘要和标签
        try:
            llm_result = llm_client.generate_summary_and_tags(ad.title, ad.ad_text)
            summary = llm_result.get("summary", "")
            raw_tags = llm_result.get("tags", [])
        except (json.JSONDecodeError, KeyError, Exception) as e:
            logger.warning(f"LLM parse failed for ad {ad_id}: {e}, using fallback")
            summary = (ad.ad_text[:50] + "…") if len(ad.ad_text) > 50 else ad.ad_text
            raw_tags = []

        # 3. 标签去重（非关键路径，失败回滚后用原始标签）
        try:
            embedding_svc = EmbeddingService()
            normalized_tags = await _normalize_tags(db, raw_tags, embedding_svc, ad.ad_text)
        except Exception as e:
            logger.warning(f"Tag normalization failed for ad {ad_id}: {e}, using raw tags")
            await db.rollback()
            normalized_tags = raw_tags

        # 4. 更新广告（先提交标签，embedding 失败不影响主流程）
        ad.ai_summary = summary
        ad.ai_tags = normalized_tags
        await db.flush()

        # 5. 生成 embedding 并写入 pgvector（非关键路径，失败不重试）
        try:
            summary_embedding = await embedding_svc.embed(summary)
            vector_store = VectorStore(db)
            await vector_store.upsert(ad_id, summary_embedding)
            await db.flush()
        except Exception as e:
            logger.warning(f"Embedding failed for ad {ad_id}: {e}, skipping")

        await db.commit()

        logger.info(f"AI pipeline completed for ad {ad_id}: tags={len(normalized_tags)}")


async def _normalize_tags(
    db,
    raw_tags: list[dict],
    embedding_svc: EmbeddingService,
    ad_text: str,
) -> list[dict]:
    """标签语义去重：检索相似已有标签 → LLM 二分类判断同义"""
    normalized = []

    for tag in raw_tags:
        tag_value = tag.get("value", "")
        tag_category = tag.get("category", "")
        if not tag_value:
            continue

        try:
            # pgvector 检索相似标签
            tag_emb = await embedding_svc.embed(tag_value)
            vector_store = VectorStore(db)
            similar_tags = await vector_store.search_similar_tags(tag_emb, threshold=0.85, top_k=3)

            found_existing = False
            for existing in similar_tags:
                if existing == tag_value:
                    found_existing = True
                    normalized.append({"category": tag_category, "value": existing})
                    break

                # LLM 二分类判断
                if llm_client.check_synonym(tag_value, existing, ad_text):
                    normalized.append({"category": tag_category, "value": existing})
                    found_existing = True
                    break

            if not found_existing:
                normalized.append(tag)

        except Exception as e:
            logger.warning(f"Tag normalization failed for '{tag_value}': {e}")
            await db.rollback()
            normalized.append(tag)

    return normalized
