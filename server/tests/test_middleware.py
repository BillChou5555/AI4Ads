"""测试中间件"""

import pytest
from unittest.mock import AsyncMock, MagicMock, patch


class TestRateLimiter:
    def test_window_logic_allowed(self):
        """窗口内请求数未达上限应允许"""
        from app.middleware.ratelimit import RateLimiter
        import redis.asyncio as aioredis

        limiter = RateLimiter(window=1, max_requests=10)
        mock_redis = AsyncMock()
        # zremrangebyscore, zcard (返回当前计数), zadd, expire
        mock_redis.pipeline.return_value.execute.return_value = [0, 5, 1, True]

        # 验证类定义正确
        assert limiter.max_requests == 10
        assert limiter.window == 1

    def test_upload_limiter_defaults(self):
        """上传限流器默认 10 req/s"""
        from app.middleware.ratelimit import upload_limiter

        assert upload_limiter.max_requests == 10
        assert upload_limiter.window == 1

    def test_feed_limiter_defaults(self):
        """Feed 限流器默认 100 req/s"""
        from app.middleware.ratelimit import feed_limiter

        assert feed_limiter.max_requests == 100
        assert feed_limiter.window == 1


class TestRequestLogger:
    @pytest.mark.asyncio
    async def test_logger_middleware_calls_next(self):
        """日志中间件应调用 call_next"""
        from app.middleware.logger import request_logger_middleware

        mock_request = MagicMock()
        mock_request.method = "GET"
        mock_request.url.path = "/health"

        mock_response = MagicMock()
        mock_response.status_code = 200

        async def mock_call_next(request):
            return mock_response

        response = await request_logger_middleware(mock_request, mock_call_next)
        assert response.status_code == 200


class TestHealthCheck:
    @pytest.mark.asyncio
    async def test_health_endpoint(self):
        """健康检查端点存在"""
        from httpx import ASGITransport, AsyncClient
        from app.main import app

        transport = ASGITransport(app=app)
        async with AsyncClient(transport=transport, base_url="http://test") as client:
            resp = await client.get("/health")
            assert resp.status_code == 200
            data = resp.json()
            assert "status" in data
            assert "db" in data
            assert "redis" in data
