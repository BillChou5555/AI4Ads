"""测试 API 路由"""

import pytest
import pytest_asyncio
from httpx import ASGITransport, AsyncClient


@pytest_asyncio.fixture
async def client():
    from app.main import app
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        yield ac


class TestHealth:
    @pytest.mark.asyncio
    async def test_health_ok(self, client: AsyncClient):
        resp = await client.get("/health")
        assert resp.status_code == 200
        data = resp.json()
        assert data["status"] in ("ok", "degraded")
        assert "db" in data
        assert "redis" in data


class TestValidation:
    @pytest.mark.asyncio
    async def test_upload_missing_fields(self, client: AsyncClient):
        """缺少必填字段应返回 422"""
        resp = await client.post("/api/v1/ads/upload", json={"title": "test"})
        assert resp.status_code == 422

    @pytest.mark.asyncio
    async def test_feed_route_exists(self, client: AsyncClient):
        """feed 路由存在（DB/Redis 未连接时会抛连接异常，但不会返回 404）"""
        try:
            resp = await client.get("/api/v1/ads/feed?tab=featured")
            assert resp.status_code != 404
        except Exception as e:
            # Redis 未启动时抛出 ConnectionError，说明路由已注册且已进入处理逻辑
            assert "404" not in str(e)
            assert "not found" not in str(e).lower()

    @pytest.mark.asyncio
    async def test_search_without_query_422(self, client: AsyncClient):
        """搜索不传 q 参数应返回 422"""
        resp = await client.get("/api/v1/ads/search")
        assert resp.status_code == 422

    @pytest.mark.asyncio
    async def test_analytics_empty_events_422(self, client: AsyncClient):
        """空事件列表应返回 422"""
        resp = await client.post("/api/v1/analytics/event", json={"events": []})
        assert resp.status_code == 422


class TestAPIRoutesExist:
    """验证所有 API 路由均已注册"""

    @pytest.mark.asyncio
    async def test_openapi_schema_has_routes(self, client: AsyncClient):
        resp = await client.get("/openapi.json")
        assert resp.status_code == 200
        schema = resp.json()
        paths = schema["paths"]
        assert "/api/v1/ads/upload" in paths
        assert "/api/v1/ads/feed" in paths
        assert "/api/v1/ads/search" in paths
        assert "/api/v1/ads/{ad_id}" in paths
        assert "/api/v1/ads/{ad_id}/like" in paths
        assert "/api/v1/analytics/event" in paths
