"""广告业务逻辑服务"""

import hashlib
import uuid
from typing import Optional
from datetime import datetime

import redis.asyncio as aioredis
from sqlalchemy import and_, or_, select, text
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.ad import Ad


class AdService:
    """广告 CRUD + 分发逻辑"""

    def __init__(self, db: AsyncSession):
        self.db = db

    async def create(self, data: dict) -> Ad:
        """创建广告，content_hash 去重。未提供 AI 数据时生成 fallback。"""
        hash_key = hashlib.md5(
            f"{data['title']}|{data['provider']}|{data['ad_text']}".encode()
        ).hexdigest()

        # 查重
        existing = await self.db.execute(
            select(Ad).where(Ad.content_hash == hash_key)
        )
        existing = existing.scalar_one_or_none()
        if existing:
            return existing, True

        ai_summary = data.get("ai_summary")
        ai_tags = data.get("ai_tags") or []

        # 未提供 AI 摘要时生成 fallback
        if not ai_summary:
            ad_text = data["ad_text"]
            ai_summary = (ad_text[:50] + "…") if len(ad_text) > 50 else ad_text

        ad = Ad(
            title=data["title"],
            provider=data["provider"],
            ad_text=data["ad_text"],
            ad_images=data.get("ad_images", []),
            ad_video_audio=data.get("ad_video_audio"),
            ai_summary=ai_summary,
            ai_tags=ai_tags,
            tab=data.get("tab", "featured"),
            content_hash=hash_key,
            status=1,
        )
        self.db.add(ad)
        await self.db.flush()
        return ad, False

    async def get_by_id(self, ad_id: uuid.UUID) -> Optional[Ad]:
        result = await self.db.execute(select(Ad).where(Ad.id == ad_id))
        return result.scalar_one_or_none()

    async def delete(self, ad_id: uuid.UUID) -> bool:
        """硬删除广告，返回是否删除成功"""
        from sqlalchemy import delete as sa_delete
        result = await self.db.execute(
            sa_delete(Ad).where(Ad.id == ad_id)
        )
        await self.db.flush()
        return result.rowcount > 0

    async def query_feed(
        self,
        tab: str,
        cursor: Optional[str] = None,
        size: int = 20,
    ) -> list[Ad]:
        """cursor-based 分页查询，按 created_at DESC, id DESC 排序（id 作为 tiebreaker）"""
        conditions = [Ad.tab == tab, Ad.status == 1]
        if cursor:
            if '|' in cursor:
                ts_str, last_id = cursor.split('|', 1)
                cursor_dt = datetime.fromisoformat(ts_str)
                conditions.append(
                    or_(
                        Ad.created_at < cursor_dt,
                        and_(Ad.created_at == cursor_dt, Ad.id < last_id)
                    )
                )
            else:
                cursor_dt = datetime.fromisoformat(cursor)
                conditions.append(Ad.created_at < cursor_dt)

        query = (
            select(Ad)
            .where(and_(*conditions))
            .order_by(Ad.created_at.desc(), Ad.id.desc())
            .limit(size + 1)  # 多取一条判断是否有下一页
        )
        result = await self.db.execute(query)
        return list(result.scalars().all())

    async def search_by_tags(self, tags: list[str], limit: int = 20) -> list[Ad]:
        """按标签模糊匹配，任意关键词命中即返回"""
        from sqlalchemy import String, or_

        if not tags:
            return []

        tag_conditions = [
            Ad.ai_tags.cast(String).ilike(f"%{tag}%")
            for tag in tags
        ]
        query = (
            select(Ad)
            .where(and_(Ad.status == 1, or_(*tag_conditions)))
            .order_by(Ad.created_at.desc())
            .limit(limit)
        )
        result = await self.db.execute(query)
        return list(result.scalars().all())

    @staticmethod
    async def dedupe_viewed(
        ads: list[Ad],
        redis: aioredis.Redis,
        device_id: str,
    ) -> list[Ad]:
        """基于设备维度的曝光排重（Redis Set）"""
        if not device_id:
            return ads
        viewed_key = f"viewed:{device_id}"
        viewed_ids = await redis.smembers(viewed_key)
        return [ad for ad in ads if str(ad.id) not in viewed_ids]

    @staticmethod
    async def mark_viewed(
        ad_ids: list[uuid.UUID],
        redis: aioredis.Redis,
        device_id: str,
    ) -> None:
        """标记广告已被该设备曝光"""
        if not device_id or not ad_ids:
            return
        key = f"viewed:{device_id}"
        await redis.sadd(key, *[str(aid) for aid in ad_ids])
        # 设置过期时间 24h
        await redis.expire(key, 86400)

    @staticmethod
    async def toggle_like(
        ad_id: uuid.UUID,
        redis: aioredis.Redis,
        device_id: str,
    ) -> dict:
        """点赞/取消赞（Redis Set），防重复"""
        if not device_id:
            return {"liked": False, "conflict": True}

        key = f"likes:{device_id}"
        already = await redis.sismember(key, str(ad_id))

        if already:
            # 取消赞
            await redis.srem(key, str(ad_id))
            await redis.srem(f"ad_liked_by:{ad_id}", device_id)
            return {"liked": False, "conflict": False}
        else:
            await redis.sadd(key, str(ad_id))
            await redis.sadd(f"ad_liked_by:{ad_id}", device_id)
            await redis.expire(key, 86400 * 30)  # 30 天过期
            return {"liked": True, "conflict": False}

    async def export_all(
        self,
        tab: Optional[str] = None,
        offset: int = 0,
        limit: int = 500,
    ) -> list[Ad]:
        """导出全部有效广告，支持按 Tab 过滤和分页"""
        conditions = [Ad.status == 1]
        if tab:
            conditions.append(Ad.tab == tab)

        query = (
            select(Ad)
            .where(and_(*conditions))
            .order_by(Ad.created_at.desc())
            .offset(offset)
            .limit(limit)
        )
        result = await self.db.execute(query)
        return list(result.scalars().all())
