"""Embedding 服务封装 — 本地运行 BAAI/bge-large-zh-v1.5"""

import logging
from typing import Optional

import redis.asyncio as aioredis

from app.config import settings

logger = logging.getLogger(__name__)

# 模型全局单例（每个进程加载一次）
_model = None


def _get_model():
    """懒加载 BGE 模型，全局单例"""
    global _model
    if _model is None:
        from sentence_transformers import SentenceTransformer
        logger.info(f"Loading embedding model: {settings.embedding_model} on {settings.embedding_device}")
        _model = SentenceTransformer(settings.embedding_model, device=settings.embedding_device)
    return _model


class EmbeddingService:
    """Embedding 生成服务，内建 Redis 缓存"""

    def __init__(self, redis_client: Optional[aioredis.Redis] = None):
        self.redis = redis_client

    async def embed(self, text: str) -> list[float]:
        """对文本生成 embedding 向量，1024 维"""
        # Redis 缓存
        if self.redis:
            cache_key = f"emb:{settings.embedding_model}:{hash(text) & 0xFFFFFFFF}"
            cached = await self.redis.get(cache_key)
            if cached:
                import json
                return json.loads(cached)

        model = _get_model()
        vector = model.encode(text, normalize_embeddings=True).tolist()

        if self.redis:
            import json
            cache_key = f"emb:{settings.embedding_model}:{hash(text) & 0xFFFFFFFF}"
            await self.redis.set(cache_key, json.dumps(vector), ex=3600)

        return vector

    async def embed_batch(self, texts: list[str]) -> list[list[float]]:
        """批量生成 embedding 向量"""
        model = _get_model()
        vectors = model.encode(texts, normalize_embeddings=True).tolist()
        return vectors
