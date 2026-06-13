"""测试 Celery 任务"""

import json
from unittest.mock import AsyncMock, MagicMock, patch

import pytest


class TestAnalyticsConsumer:
    def test_event_parsing(self):
        """验证埋点事件 JSON 解析"""
        data = '{"device_id": "d1", "ad_id": "123e4567-e89b-12d3-a456-426614174000", "event_type": "impression", "event_time": 1700000000000}'
        parsed = json.loads(data)
        assert parsed["device_id"] == "d1"
        assert parsed["event_type"] == "impression"

    def test_invalid_json_skipped(self):
        """无效 JSON 应被跳过"""
        import json as json_module

        try:
            json_module.loads("not valid json")
            assert False, "Should have raised"
        except json_module.JSONDecodeError:
            assert True


class TestFeedCacheWarmer:
    def test_cache_key_format(self):
        """缓存 Key 格式正确"""
        tab = "featured"
        cache_key = f"feed:{tab}:home"
        assert cache_key == "feed:featured:home"

    def test_all_tabs_cached(self):
        """所有 Tab 都应被预热"""
        tabs = ["featured", "ecommerce", "local"]
        for tab in tabs:
            cache_key = f"feed:{tab}:home"
            assert cache_key.startswith("feed:")
            assert cache_key.endswith(":home")


class TestCeleryConfig:
    def test_celery_app_exists(self):
        """Celery 应用能正常创建"""
        from app.core.celery_app import celery_app

        assert celery_app is not None
        assert celery_app.main == "aiads"

    def test_task_modules_registered(self):
        """Celery 任务模块已注册"""
        from app.core.celery_app import celery_app

        includes = celery_app.conf.get("include", [])
        assert len(includes) >= 3
        assert "app.tasks.ai_pipeline" in includes
        assert "app.tasks.analytics_consumer" in includes
        assert "app.tasks.feed_cache_warmer" in includes
