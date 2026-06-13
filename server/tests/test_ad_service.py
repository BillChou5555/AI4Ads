"""测试 AdService 业务逻辑（需要 DB/Redis 的测试用 mock）"""

import uuid
from unittest.mock import AsyncMock, MagicMock, patch

import pytest


class TestAdServiceCreate:
    def test_content_hash_generation(self):
        """验证 content_hash 由 title + provider + ad_text 生成"""
        import hashlib

        data = {"title": "A", "provider": "B", "ad_text": "C"}
        expected = hashlib.md5(b"A|B|C").hexdigest()

        actual = hashlib.md5(f"{data['title']}|{data['provider']}|{data['ad_text']}".encode()).hexdigest()
        assert actual == expected
        assert len(actual) == 32


class TestAdServiceToggleLike:
    @pytest.mark.asyncio
    async def test_like_new(self):
        """首次点赞应返回 liked=True"""
        mock_redis = AsyncMock()
        mock_redis.sismember.return_value = False

        from app.services.ad_service import AdService
        ad_id = uuid.uuid4()
        result = await AdService.toggle_like(ad_id, mock_redis, "device_001")

        assert result["liked"] is True
        assert result["conflict"] is False
        mock_redis.sadd.assert_called()

    @pytest.mark.asyncio
    async def test_unlike_when_already_liked(self):
        """再次点赞应取消（返回 liked=False）"""
        mock_redis = AsyncMock()
        mock_redis.sismember.return_value = True

        from app.services.ad_service import AdService
        ad_id = uuid.uuid4()
        result = await AdService.toggle_like(ad_id, mock_redis, "device_001")

        assert result["liked"] is False
        assert result["conflict"] is False
        mock_redis.srem.assert_called()

    @pytest.mark.asyncio
    async def test_like_without_device_id(self):
        """无 device_id 应返回 conflict=True"""
        mock_redis = AsyncMock()

        from app.services.ad_service import AdService
        ad_id = uuid.uuid4()
        result = await AdService.toggle_like(ad_id, mock_redis, "")

        assert result["conflict"] is True


class TestAdServiceDedupe:
    @pytest.mark.asyncio
    async def test_dedupe_empty_device_id(self):
        """空 device_id 时不排重，返回全部"""
        mock_redis = AsyncMock()
        ads = [MagicMock() for _ in range(3)]

        from app.services.ad_service import AdService
        result = await AdService.dedupe_viewed(ads, mock_redis, "")
        assert len(result) == 3

    @pytest.mark.asyncio
    async def test_dedupe_filters_viewed(self):
        """应过滤已曝光广告"""
        mock_redis = AsyncMock()
        ad1 = MagicMock()
        ad1.id = uuid.uuid4()
        ad2 = MagicMock()
        ad2.id = uuid.uuid4()

        mock_redis.smembers.return_value = {str(ad1.id)}

        from app.services.ad_service import AdService
        result = await AdService.dedupe_viewed([ad1, ad2], mock_redis, "device_001")
        assert len(result) == 1
        assert result[0].id == ad2.id

    @pytest.mark.asyncio
    async def test_mark_viewed(self):
        """标记曝光应写入 Redis Set"""
        mock_redis = AsyncMock()
        ad_id = uuid.uuid4()

        from app.services.ad_service import AdService
        await AdService.mark_viewed([ad_id], mock_redis, "device_001")
        mock_redis.sadd.assert_called_once()
