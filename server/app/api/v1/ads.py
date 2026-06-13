"""广告相关 API"""

import json
import logging
from uuid import UUID

import redis.asyncio as aioredis
from fastapi import APIRouter, Depends, Header, HTTPException, Query
from sqlalchemy import and_, or_, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.deps import get_device_id
from app.core.celery_app import celery_app
from app.core.database import get_db
from app.core.redis_client import get_redis
from fastapi.responses import StreamingResponse

from app.schemas.ad import (
    AdDetailResponse,
    AdItem,
    AdUploadRequest,
    AdUploadResponse,
    FeedResponse,
    LikeResponse,
)
from app.services.ad_service import AdService
from app.services.embedding_service import EmbeddingService
from app.services.llm_service import llm_client
from app.services.vector_store import VectorStore

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/ads", tags=["ads"])


@router.post("/upload", response_model=AdUploadResponse)
async def upload_ad(
    req: AdUploadRequest,
    db: AsyncSession = Depends(get_db),
) -> AdUploadResponse:
    """物料入库接口"""
    ad_service = AdService(db)
    ad, is_dup = await ad_service.create(req.model_dump())

    await db.commit()

    # 未提供 AI 数据时触发异步管线生成，已提供则跳过
    if not is_dup and not (req.ai_summary and req.ai_tags):
        try:
            celery_app.send_task(
                "app.tasks.ai_pipeline.process_ad_summary",
                args=[str(ad.id)],
            )
        except Exception:
            logger.warning("Celery broker 不可用，跳过 AI 管线触发，ad_id=%s", ad.id)

    return AdUploadResponse(
        ad_id=ad.id,
        dup=is_dup,
    )


@router.get("/feed", response_model=FeedResponse)
async def get_feed(
    tab: str = Query(default="featured", description="Tab 名称"),
    cursor: str | None = Query(default=None, description="上一页最后一条 created_at (ISO 8601)"),
    size: int = Query(default=20, ge=1, le=50, description="每页条数"),
    device_id: str = Query(default="", description="设备 ID（兼容 query 和 header）"),
    x_device_id: str = Header(default="", alias="X-Device-ID"),
    db: AsyncSession = Depends(get_db),
    redis: aioredis.Redis = Depends(get_redis),
) -> FeedResponse:
    """Feed 分发接口：cursor-based 分页 + 曝光排重"""
    # 兼容 query 参数和 header 两种 device_id 传法
    effective_device_id = device_id or x_device_id
    ad_service = AdService(db)

    # 首页（无 cursor）查 Redis 缓存
    if not cursor:
        cache_key = f"feed:{tab}:home"
        cached = await redis.get(cache_key)
        if cached:
            items_data = json.loads(cached)
            # 曝光排重
            viewed_ids = set()
            if effective_device_id:
                viewed_ids = await redis.smembers(f"viewed:{effective_device_id}")
            items_data = [it for it in items_data if it["id"] not in viewed_ids]
            return FeedResponse(
                items=[AdItem(**it) for it in items_data],
                next_cursor=f"{items_data[-1]['created_at']}|{items_data[-1]['id']}" if items_data else None,
            )

    ads = await ad_service.query_feed(tab=tab, cursor=cursor, size=size)
    ads = await ad_service.dedupe_viewed(ads, redis, effective_device_id)

    has_more = len(ads) > size
    if has_more:
        ads = ads[:size]

    items = [AdItem.model_validate(ad) for ad in ads]
    next_cursor = f"{ads[-1].created_at.isoformat()}|{ads[-1].id}" if has_more else None

    return FeedResponse(items=items, next_cursor=next_cursor)


@router.get("/export")
async def export_ads(
    tab: str | None = Query(default=None, description="按 Tab 过滤，不传则导出全部"),
    db: AsyncSession = Depends(get_db),
):
    """导出广告库：将所有有效广告按导入 JSON 格式下载"""
    import io
    import json

    ad_service = AdService(db)
    all_ads = []
    offset = 0
    batch_size = 500

    while True:
        batch = await ad_service.export_all(tab=tab, offset=offset, limit=batch_size)
        if not batch:
            break
        for ad in batch:
            all_ads.append({
                "title": ad.title,
                "provider": ad.provider,
                "ad_text": ad.ad_text,
                "tab": ad.tab,
                "ad_images": ad.ad_images or [],
                "ad_video_audio": ad.ad_video_audio,
                "ai_summary": ad.ai_summary,
                "ai_tags": ad.ai_tags or [],
            })
        offset += batch_size

    json_bytes = json.dumps(all_ads, ensure_ascii=False, indent=2).encode("utf-8")
    return StreamingResponse(
        io.BytesIO(json_bytes),
        media_type="application/json",
        headers={"Content-Disposition": "attachment; filename=ads_export.json"},
    )


@router.get("/search", response_model=FeedResponse)
async def search_ads(
    q: str = Query(..., min_length=1, description="自然语言搜索查询"),
    page: int = Query(default=1, ge=1),
    page_size: int = Query(default=20, ge=1, le=50),
    db: AsyncSession = Depends(get_db),
) -> FeedResponse:
    """自然语言搜索：意图识别 → 向量检索 → LLM 重排序"""
    ad_service = AdService(db)
    vector_store = VectorStore(db)
    embedding_svc = EmbeddingService()

    # Step 1: 意图识别（LLM 提取关键词）
    keywords = [q]
    try:
        extracted = llm_client.extract_intent(q)
        if extracted:
            keywords = extracted
    except Exception as e:
        logger.warning(f"Intent extraction failed: {e}, using raw query")

    # Step 2: 向量检索
    candidates: list[dict] = []
    try:
        query_embedding = await embedding_svc.embed(q)
        candidates = await vector_store.search(query_embedding, top_k=50)
    except Exception as e:
        logger.warning(f"Vector search failed: {e}, falling back to keyword search")

    # Step 3: 关键词 fallback（补充无 embedding 的广告）
    candidate_ids = {c["id"] for c in candidates}
    if len(candidates) < page_size * page:
        try:
            keyword_ads = await ad_service.search_by_tags(keywords, limit=50)
            for ad in keyword_ads:
                if str(ad.id) not in candidate_ids:
                    ad_item = AdItem.model_validate(ad)
                    candidates.append({
                        **ad_item.model_dump(mode="json"),
                        "similarity": 0.0,
                    })
        except Exception as e:
            logger.warning(f"Keyword search failed: {e}")

    # Step 4: LLM 打分精排（0.6 阈值过滤）
    try:
        candidates = llm_client.rerank(q, candidates, score_threshold=0.6)
    except Exception as e:
        logger.warning(f"Rerank failed: {e}, falling back to similarity sort")
        candidates = [c for c in candidates if c.get("similarity", 0) >= 0.3]
        candidates.sort(key=lambda c: c.get("similarity", 0), reverse=True)

    # Step 5: 分页
    total = len(candidates)
    start = (page - 1) * page_size
    end = start + page_size
    page_items = candidates[start:end]

    # 清理内部字段（AdItem 不需要）
    for item in page_items:
        item.pop("similarity", None)
        item.pop("score", None)

    next_cursor = str(page + 1) if end < total else None
    return FeedResponse(
        items=[AdItem(**item) for item in page_items],
        next_cursor=next_cursor,
    )


@router.get("/{ad_id}")
async def get_ad_detail(
    ad_id: UUID,
    db: AsyncSession = Depends(get_db),
):
    """广告详情接口，直接返回 AdItem"""
    ad_service = AdService(db)
    ad = await ad_service.get_by_id(ad_id)
    if not ad:
        raise HTTPException(status_code=404, detail="ad not found")

    return AdItem.model_validate(ad)


@router.post("/{ad_id}/like", response_model=LikeResponse)
async def like_ad(
    ad_id: UUID,
    db: AsyncSession = Depends(get_db),
    redis: aioredis.Redis = Depends(get_redis),
    device_id: str = Depends(get_device_id),
) -> LikeResponse:
    """点赞/取消赞接口"""
    ad_service = AdService(db)

    # 验证广告存在
    ad = await ad_service.get_by_id(ad_id)
    if not ad:
        raise HTTPException(status_code=404, detail="ad not found")

    result = await ad_service.toggle_like(ad_id, redis, device_id)
    if result["conflict"]:
        raise HTTPException(status_code=400, detail="missing device_id")

    return LikeResponse()


@router.delete("/{ad_id}")
async def delete_ad(
    ad_id: UUID,
    db: AsyncSession = Depends(get_db),
):
    """硬删除广告"""
    ad_service = AdService(db)
    deleted = await ad_service.delete(ad_id)
    if not deleted:
        raise HTTPException(status_code=404, detail="ad not found")
    await db.commit()
    return {"code": 0, "detail": "deleted"}
