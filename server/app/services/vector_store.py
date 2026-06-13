"""pgvector 向量存储封装"""

from typing import Optional

from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.ad import Ad


class VectorStore:
    """pgvector 操作封装"""

    def __init__(self, db: AsyncSession):
        self.db = db

    async def search(
        self,
        embedding: list[float],
        top_k: int = 50,
        threshold: float = 0.2,
    ) -> list[dict]:
        """Cosine 相似度 ANN 向量检索，threshold 过滤低分噪声"""
        query = text("""
            SELECT id, title, provider, ad_text, ad_images, ad_video_audio,
                   ai_summary, ai_tags, tab, created_at,
                   1 - (embedding <=> :embedding) AS similarity
            FROM ads
            WHERE status = 1 AND embedding IS NOT NULL
              AND 1 - (embedding <=> :embedding) >= :threshold
            ORDER BY embedding <=> :embedding
            LIMIT :limit
        """)

        result = await self.db.execute(
            query,
            {"embedding": str(embedding), "limit": top_k, "threshold": threshold},
        )
        rows = result.fetchall()

        return [
            {
                "id": str(row.id),
                "title": row.title,
                "provider": row.provider,
                "ad_text": row.ad_text,
                "ad_images": row.ad_images or [],
                "ad_video_audio": row.ad_video_audio,
                "ai_summary": row.ai_summary,
                "ai_tags": row.ai_tags or [],
                "tab": row.tab,
                "created_at": row.created_at.isoformat() if row.created_at else None,
                "similarity": float(row.similarity),
            }
            for row in rows
        ]

    async def upsert(self, ad_id: str, embedding: list[float]) -> None:
        """更新广告的 embedding 向量"""
        await self.db.execute(
            text("UPDATE ads SET embedding = CAST(:embedding AS vector) WHERE id = :id"),
            {"embedding": str(embedding), "id": ad_id},
        )
        await self.db.flush()

    async def search_similar_tags(
        self,
        tag_embedding: list[float],
        threshold: float = 0.85,
        top_k: int = 3,
    ) -> list[str]:
        """检索与给定标签 embedding 相似的已有标签名"""
        query = text("""
            SELECT DISTINCT tag_obj->>'value' AS tag_value,
                   1 - (CAST(:embedding AS vector) <=> tag_emb.embedding) AS similarity
            FROM ads,
                 LATERAL jsonb_array_elements(ai_tags) AS tag_obj,
                 LATERAL (
                     SELECT CAST(:embedding AS vector) AS embedding
                 ) AS tag_emb
            WHERE status = 1
              AND ai_tags IS NOT NULL
              AND jsonb_array_length(ai_tags) > 0
            ORDER BY tag_emb.embedding <=> CAST(:embedding AS vector)
            LIMIT :limit
        """)
        result = await self.db.execute(
            query,
            {"embedding": str(tag_embedding), "limit": top_k},
        )
        rows = result.fetchall()
        return [row.tag_value for row in rows if float(row.similarity) > threshold]
