"""广告主表模型"""

import uuid
from datetime import datetime
from typing import Optional

from pgvector.sqlalchemy import Vector
from sqlalchemy import DateTime, Integer, String, Text, func
from sqlalchemy.dialects.postgresql import JSONB, UUID
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column


class Base(DeclarativeBase):
    pass


class Ad(Base):
    __tablename__ = "ads"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    title: Mapped[str] = mapped_column(String(200), nullable=False)
    provider: Mapped[str] = mapped_column(String(100), nullable=False)
    ad_text: Mapped[str] = mapped_column(Text, nullable=False)
    ad_images: Mapped[Optional[list]] = mapped_column(JSONB, default=list)
    ad_video_audio: Mapped[Optional[str]] = mapped_column(String(500), nullable=True)
    ai_summary: Mapped[Optional[str]] = mapped_column(String(100), nullable=True)
    ai_tags: Mapped[Optional[list]] = mapped_column(JSONB, default=list)
    tab: Mapped[str] = mapped_column(String(50), nullable=False, default="featured")
    status: Mapped[int] = mapped_column(Integer, nullable=False, default=1)
    content_hash: Mapped[str] = mapped_column(String(32), nullable=False, unique=True)
    embedding: Mapped[Optional[list]] = mapped_column(Vector(1024), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, server_default=func.now())
    updated_at: Mapped[datetime] = mapped_column(DateTime, server_default=func.now(), onupdate=func.now())
