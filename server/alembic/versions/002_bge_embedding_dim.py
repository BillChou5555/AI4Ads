"""change embedding dimension from 1536 to 1024 for BGE-large-zh-v1.5

Revision ID: 002
Revises: 001
Create Date: 2026-06-01
"""
from typing import Sequence, Union

from alembic import op
from pgvector.sqlalchemy import Vector
import sqlalchemy as sa


revision: str = "002"
down_revision: Union[str, None] = "001"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    # pgvector 不支持直接 ALTER COLUMN TYPE，需要先删索引再改
    op.execute("DROP INDEX IF EXISTS idx_ads_embedding")
    op.execute("ALTER TABLE ads ALTER COLUMN embedding TYPE vector(1024)")
    op.execute(
        "CREATE INDEX idx_ads_embedding ON ads USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100)"
    )


def downgrade() -> None:
    op.execute("DROP INDEX IF EXISTS idx_ads_embedding")
    op.execute("ALTER TABLE ads ALTER COLUMN embedding TYPE vector(1536)")
    op.execute(
        "CREATE INDEX idx_ads_embedding ON ads USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100)"
    )
