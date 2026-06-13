"""init ads and device_events schema

Revision ID: 001
Revises:
Create Date: 2026-06-01
"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa
from pgvector.sqlalchemy import Vector
from sqlalchemy.dialects import postgresql


revision: str = "001"
down_revision: Union[str, None] = None
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.execute("CREATE EXTENSION IF NOT EXISTS vector")

    op.create_table(
        "ads",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True, server_default=sa.text("gen_random_uuid()")),
        sa.Column("title", sa.String(200), nullable=False),
        sa.Column("provider", sa.String(100), nullable=False),
        sa.Column("ad_text", sa.Text, nullable=False),
        sa.Column("ad_images", postgresql.JSONB, server_default=sa.text("'[]'::jsonb")),
        sa.Column("ad_video_audio", sa.String(500), nullable=True),
        sa.Column("ai_summary", sa.String(100), nullable=True),
        sa.Column("ai_tags", postgresql.JSONB, server_default=sa.text("'[]'::jsonb")),
        sa.Column("tab", sa.String(50), nullable=False, server_default="featured"),
        sa.Column("status", sa.SmallInteger, nullable=False, server_default=sa.text("1")),
        sa.Column("content_hash", sa.String(32), nullable=False),
        sa.Column("embedding", Vector(1536), nullable=True),
        sa.Column("created_at", sa.DateTime, server_default=sa.text("NOW()")),
        sa.Column("updated_at", sa.DateTime, server_default=sa.text("NOW()")),
    )

    op.execute(
        "CREATE INDEX idx_ads_tab_created ON ads (tab, created_at DESC) WHERE status = 1"
    )
    op.create_index("idx_ads_content_hash", "ads", ["content_hash"], unique=True)
    op.execute(
        "CREATE INDEX idx_ads_embedding ON ads USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100)"
    )

    op.create_table(
        "device_events",
        sa.Column("id", sa.BigInteger, primary_key=True, autoincrement=True),
        sa.Column("device_id", sa.String(64), nullable=False),
        sa.Column("ad_id", postgresql.UUID(as_uuid=True), sa.ForeignKey("ads.id"), nullable=False),
        sa.Column("event_type", sa.String(20), nullable=False),
        sa.Column("event_data", postgresql.JSONB, server_default=sa.text("'{}'::jsonb")),
        sa.Column("created_at", sa.DateTime, server_default=sa.text("NOW()")),
    )

    op.execute(
        "CREATE INDEX idx_device_events_device ON device_events (device_id, created_at DESC)"
    )


def downgrade() -> None:
    op.drop_table("device_events")
    op.drop_table("ads")
