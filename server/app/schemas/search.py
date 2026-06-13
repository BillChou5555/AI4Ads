"""搜索相关 Schema"""

from typing import List

from pydantic import BaseModel, Field

from app.schemas.ad import AdItem


class SearchRequest(BaseModel):
    q: str = Field(..., min_length=1, max_length=500, description="搜索关键词")
    page: int = Field(default=1, ge=1, description="页码")
    page_size: int = Field(default=20, ge=1, le=50, description="每页条数")


class SearchResponse(BaseModel):
    items: List[AdItem]
    total: int
    page: int
    page_size: int
