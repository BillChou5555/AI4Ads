"""SQLite 测试数据库工具：为集成测试提供内存数据库

pgvector 的 Vector 类型不支持 SQLite，embedding 列用 TEXT 代替，
pgvector 的向量运算（<=> 等）在集成测试中通过 mock 处理。
"""

from typing import AsyncGenerator

from sqlalchemy import Column, DateTime, Integer, MetaData, String, Table, Text, func
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine
import sqlalchemy as sa

TEST_DB_URL = "sqlite+aiosqlite:///file:test_integration?mode=memory&cache=shared&uri=true"

metadata = MetaData()

ads_table = Table(
    "ads",
    metadata,
    Column("id", String(36), primary_key=True),
    Column("title", String(200), nullable=False),
    Column("provider", String(100), nullable=False),
    Column("ad_text", Text, nullable=False),
    Column("ad_images", Text, default="[]"),
    Column("ad_video_audio", String(500), nullable=True),
    Column("ai_summary", String(100), nullable=True),
    Column("ai_tags", Text, default="[]"),
    Column("tab", String(50), nullable=False, default="featured"),
    Column("status", Integer, nullable=False, default=1),
    Column("content_hash", String(32), nullable=False, unique=True),
    Column("created_at", DateTime, server_default=func.now()),
    Column("updated_at", DateTime, server_default=func.now()),
    Column("embedding", Text, nullable=True),  # pgvector → TEXT 占位
)

device_events_table = Table(
    "device_events",
    metadata,
    Column("id", Integer, primary_key=True, autoincrement=True),
    Column("device_id", String(64), nullable=False),
    Column("ad_id", String(36), nullable=False),
    Column("event_type", String(20), nullable=False),
    Column("event_data", Text, default="{}"),
    Column("created_at", DateTime, server_default=func.now()),
)

_engine = create_async_engine(TEST_DB_URL, echo=False)
_test_session_factory = async_sessionmaker(_engine, class_=AsyncSession, expire_on_commit=False)


async def create_test_tables():
    """创建测试用表（每次测试前调用）"""
    async with _engine.begin() as conn:
        await conn.run_sync(metadata.create_all)


async def drop_test_tables():
    """删除测试用表（每次测试后调用）"""
    async with _engine.begin() as conn:
        await conn.run_sync(metadata.drop_all)


async def get_test_db() -> AsyncGenerator[AsyncSession, None]:
    async with _test_session_factory() as session:
        try:
            yield session
        finally:
            await session.close()
