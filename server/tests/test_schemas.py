"""测试 Pydantic Schema 校验"""

import pytest
from pydantic import ValidationError

from app.schemas.ad import AdItem, AdUploadRequest, AdUploadResponse
from app.schemas.analytics import AnalyticsEvent, AnalyticsEventList
from app.schemas.search import SearchResponse


class TestAdUploadRequest:
    def test_valid_request_minimal(self):
        """最小必填字段应通过校验"""
        req = AdUploadRequest(title="测试广告", provider="测试商家", ad_text="这是测试文本")
        assert req.title == "测试广告"
        assert req.provider == "测试商家"
        assert req.ad_images == []
        assert req.ad_video_audio is None

    def test_valid_request_full(self):
        """全字段应通过校验"""
        req = AdUploadRequest(
            title="测试广告",
            provider="测试商家",
            ad_text="这是测试文本",
            ad_images=["https://example.com/img1.jpg"],
            ad_video_audio="https://example.com/video.mp4",
            tab="ecommerce",
        )
        assert len(req.ad_images) == 1
        assert req.ad_video_audio == "https://example.com/video.mp4"
        assert req.tab == "ecommerce"

    def test_missing_title(self):
        """缺少 title 应抛 ValidationError"""
        with pytest.raises(ValidationError):
            AdUploadRequest(provider="商家", ad_text="文本")

    def test_missing_provider(self):
        """缺少 provider 应抛 ValidationError"""
        with pytest.raises(ValidationError):
            AdUploadRequest(title="标题", ad_text="文本")

    def test_missing_ad_text(self):
        """缺少 ad_text 应抛 ValidationError"""
        with pytest.raises(ValidationError):
            AdUploadRequest(title="标题", provider="商家")

    def test_title_too_long(self):
        """title 超过 200 字符应抛 ValidationError"""
        with pytest.raises(ValidationError):
            AdUploadRequest(title="x" * 201, provider="商家", ad_text="文本")

    def test_title_empty(self):
        """title 为空应抛 ValidationError"""
        with pytest.raises(ValidationError):
            AdUploadRequest(title="", provider="商家", ad_text="文本")


class TestAnalyticsEvent:
    def test_valid_event(self):
        """有效埋点事件应通过校验"""
        e = AnalyticsEvent(device_id="device_001", event_type="impression", event_time=1700000000000)
        assert e.device_id == "device_001"
        assert e.event_type == "impression"

    def test_event_list_valid(self):
        """批量事件应通过校验"""
        events = [
            AnalyticsEvent(device_id="d1", event_type="impression", event_time=1700000000000),
            AnalyticsEvent(device_id="d2", event_type="click", event_time=1700000000001),
        ]
        body = AnalyticsEventList(events=events)
        assert len(body.events) == 2

    def test_event_list_empty(self):
        """空列表应抛 ValidationError"""
        with pytest.raises(ValidationError):
            AnalyticsEventList(events=[])

    def test_event_list_too_many(self):
        """超过 100 条应抛 ValidationError"""
        with pytest.raises(ValidationError):
            AnalyticsEventList(events=[AnalyticsEvent(device_id="d1", event_type="impression", event_time=1700000000000)] * 101)

    def test_event_type_invalid(self):
        """event_type 为特殊值不应影响校验（自由文本字段）"""
        e = AnalyticsEvent(device_id="d1", event_type="custom_event", event_time=1700000000000)
        assert e.event_type == "custom_event"


class TestFeedResponseSchema:
    def test_empty_feed(self):
        """空 Feed 响应格式正确"""
        from app.schemas.ad import FeedResponse
        resp = FeedResponse(items=[], next_cursor=None)
        assert resp.items == []
        assert resp.next_cursor is None

    def test_feed_with_items(self):
        """带数据 Feed 响应格式正确"""
        from app.schemas.ad import FeedResponse, AdItem, AdTag
        from uuid import uuid4
        from datetime import datetime

        item = AdItem(
            id=uuid4(),
            title="test",
            provider="test_provider",
            ad_text="test text",
            ad_images=[],
            ai_tags=[],
            tab="featured",
            created_at=datetime.now(),
        )
        resp = FeedResponse(items=[item], next_cursor="2026-06-01T00:00:00")
        assert len(resp.items) == 1
        assert resp.next_cursor == "2026-06-01T00:00:00"
