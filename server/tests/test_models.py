"""测试数据模型定义"""


def test_ad_model_columns():
    """验证 Ad 模型字段完整性"""
    from app.models.ad import Ad
    from sqlalchemy.orm import Mapped

    columns = {c.name: c for c in Ad.__table__.columns}

    assert "id" in columns
    assert "title" in columns
    assert "provider" in columns
    assert "ad_text" in columns
    assert "ad_images" in columns
    assert "ad_video_audio" in columns
    assert "ai_summary" in columns
    assert "ai_tags" in columns
    assert "tab" in columns
    assert "status" in columns
    assert "content_hash" in columns
    assert "embedding" in columns
    assert "created_at" in columns
    assert "updated_at" in columns

    # content_hash 应有唯一约束（通过 mapped_column unique=True 定义）
    assert columns["content_hash"].unique


def test_device_event_model_columns():
    """验证 DeviceEvent 模型字段完整性"""
    from app.models.device_event import DeviceEvent

    columns = {c.name for c in DeviceEvent.__table__.columns}

    assert "id" in columns
    assert "device_id" in columns
    assert "ad_id" in columns
    assert "event_type" in columns
    assert "event_data" in columns
    assert "created_at" in columns


def test_app_creates():
    """验证 FastAPI 应用能正常创建"""
    from app.main import app

    assert app.title == "AI Ads Feed API"
    assert app.version == "0.1.0"

    routes = {r.path for r in app.routes}
    assert "/health" in routes
    assert "/openapi.json" in routes
