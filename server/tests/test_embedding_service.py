"""测试 Embedding 服务"""

import json
from unittest.mock import AsyncMock, MagicMock, patch

import pytest

from app.services.embedding_service import EmbeddingService


class TestEmbed:
    @pytest.mark.asyncio
    async def test_embed_returns_correct_dimension(self):
        svc = EmbeddingService(redis_client=None)
        svc.client = MagicMock()
        mock_response = MagicMock()
        mock_response.data = [MagicMock()]
        mock_response.data[0].embedding = [0.1] * 1536
        svc.client.embeddings.create.return_value = mock_response

        result = await svc.embed("test text")
        assert len(result) == 1536

    @pytest.mark.asyncio
    async def test_embed_with_cache_hit(self):
        mock_redis = AsyncMock()
        mock_redis.get.return_value = json.dumps([0.2] * 1536)

        svc = EmbeddingService(redis_client=mock_redis)
        result = await svc.embed("cached text")
        assert len(result) == 1536
        assert result[0] == 0.2
        mock_redis.get.assert_called()

    @pytest.mark.asyncio
    async def test_embed_cache_miss_calls_api(self):
        mock_redis = AsyncMock()
        mock_redis.get.return_value = None

        svc = EmbeddingService(redis_client=mock_redis)
        svc.client = MagicMock()
        mock_response = MagicMock()
        mock_response.data = [MagicMock()]
        mock_response.data[0].embedding = [0.3] * 1536
        svc.client.embeddings.create.return_value = mock_response

        result = await svc.embed("new text")
        assert len(result) == 1536
        mock_redis.set.assert_called()

    @pytest.mark.asyncio
    async def test_embed_batch(self):
        svc = EmbeddingService(redis_client=None)
        svc.client = MagicMock()
        mock_response = MagicMock()
        mock_response.data = [MagicMock()]
        mock_response.data[0].embedding = [0.1] * 1536
        svc.client.embeddings.create.return_value = mock_response

        results = await svc.embed_batch(["text1", "text2"])
        assert len(results) == 2
        assert len(results[0]) == 1536
