"""RAG 搜索服务 — 向量阈值过滤 + LLM 打分精排"""

import logging
from typing import Optional

import redis.asyncio as aioredis
from sqlalchemy.ext.asyncio import AsyncSession

from app.schemas.ad import AdItem, FeedResponse
from app.services.ad_service import AdService
from app.services.embedding_service import EmbeddingService
from app.services.llm_service import llm_client
from app.services.vector_store import VectorStore

logger = logging.getLogger(__name__)


class SearchService:
    """RAG 搜索：向量粗筛(0.2) → LLM 打分(0.6) → 分页返回"""

    VECTOR_THRESHOLD = 0.2
    SCORE_THRESHOLD = 0.6

    def __init__(
        self,
        db: AsyncSession,
        redis: Optional[aioredis.Redis] = None,
    ):
        self.db = db
        self.redis = redis
        self.embedding = EmbeddingService(redis_client=redis)
        self.vector_store = VectorStore(db)
        self.ad_service = AdService(db)

    async def search(
        self,
        query: str,
        page: int = 1,
        page_size: int = 20,
    ) -> FeedResponse:
        candidates: list[dict] = []

        # Step 1: 向量检索（0.2 阈值粗筛）
        try:
            query_embedding = await self.embedding.embed(query)
            candidates = await self.vector_store.search(
                query_embedding, top_k=50, threshold=self.VECTOR_THRESHOLD,
            )
        except Exception as e:
            logger.warning(f"Vector search failed: {e}")

        # Step 2: 关键词补充（无 embedding 广告的兜底）
        if len(candidates) < page_size * page:
            try:
                keywords = [query]
                try:
                    extracted = llm_client.extract_intent(query)
                    if extracted:
                        keywords = extracted
                except Exception:
                    pass

                candidate_ids = {c["id"] for c in candidates}
                keyword_ads = await self.ad_service.search_by_tags(keywords, limit=50)
                for ad in keyword_ads:
                    if str(ad.id) not in candidate_ids:
                        item = AdItem.model_validate(ad)
                        candidates.append({**item.model_dump(mode="json"), "similarity": 0.0})
            except Exception as e:
                logger.warning(f"Keyword fallback failed: {e}")

        if not candidates:
            return FeedResponse(items=[], next_cursor=None)

        # Step 3: LLM 独立打分精排（0.6 阈值过滤）
        try:
            candidates = llm_client.rerank(query, candidates, score_threshold=self.SCORE_THRESHOLD)
        except Exception as e:
            logger.warning(f"LLM rerank failed: {e}, falling back to similarity filter")
            candidates = [c for c in candidates if c.get("similarity", 0) >= 0.3]
            candidates.sort(key=lambda c: c.get("similarity", 0), reverse=True)

        # Step 4: 分页
        total = len(candidates)
        start = (page - 1) * page_size
        end = start + page_size
        page_items = candidates[start:end]

        for item in page_items:
            item.pop("similarity", None)
            item.pop("score", None)

        next_cursor = str(page + 1) if end < total else None
        return FeedResponse(
            items=[AdItem(**item) for item in page_items],
            next_cursor=next_cursor,
        )
