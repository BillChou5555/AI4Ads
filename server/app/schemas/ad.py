"""广告相关 Schema"""

from datetime import datetime
from typing import List, Optional
from uuid import UUID

from pydantic import BaseModel, Field, model_validator


# -- 广告标签 --

class AdTag(BaseModel):
    category: str
    value: str


# -- 物料上传 --

class AdUploadRequest(BaseModel):
    title: str = Field(..., min_length=1, max_length=200, description="广告标题")
    provider: str = Field(..., min_length=1, max_length=100, description="广告提供商")
    ad_text: str = Field(..., min_length=1, description="广告文本内容")
    ad_images: Optional[List[str]] = Field(default=[], description="图片 URL 列表")
    ad_video_audio: Optional[str] = Field(default=None, max_length=500, description="视频 URL")
    tab: str = Field(default="featured", description="所属 Tab")
    ai_summary: Optional[str] = Field(default=None, max_length=100, description="AI 摘要（可选）")
    ai_tags: Optional[List[AdTag]] = Field(default=None, description="AI 标签（可选）")


class AdUploadResponse(BaseModel):
    code: int = 0
    ad_id: UUID
    dup: bool = False


# -- 广告条目（返回给客户端）--

class AdItem(BaseModel):
    id: UUID
    title: str
    provider: str
    ad_text: str
    ad_images: List[str] = []
    ad_video_audio: Optional[str] = None
    ai_summary: Optional[str] = None
    ai_tags: List[AdTag] = []
    tab: str
    created_at: datetime

    model_config = {"from_attributes": True}

    @model_validator(mode="after")
    def ensure_summary(self):
        """兜底：旧数据或 Celery 失败时 ai_summary 可能为 None，自动生成 fallback"""
        if not self.ai_summary:
            text = self.ad_text or ""
            self.ai_summary = (text[:50] + "…") if len(text) > 50 else text
        return self


# -- Feed 分发 --

class FeedResponse(BaseModel):
    items: List[AdItem]
    next_cursor: Optional[str] = None


# -- 点赞 --

class LikeResponse(BaseModel):
    code: int = 0


# -- 广告详情 --

class AdDetailResponse(BaseModel):
    code: int = 0
    ad: AdItem
