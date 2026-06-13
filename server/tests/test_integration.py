"""API 集成测试：完整请求/响应链路，使用 SQLite 内存数据库 + fakeredis"""

import json
import uuid

import pytest
import pytest_asyncio
from httpx import ASGITransport, AsyncClient

from app.core.database import get_db
from app.core.redis_client import get_redis


@pytest_asyncio.fixture
async def client(test_db, test_redis):
    """FastAPI test client，DB 和 Redis 均已注入测试实现"""
    from app.main import app

    # 覆盖依赖
    app.dependency_overrides[get_db] = lambda: test_db
    app.dependency_overrides[get_redis] = lambda: test_redis

    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        yield ac

    app.dependency_overrides.clear()


class TestUploadAndFeed:
    """上传 → Feed 完整链路"""

    @pytest.mark.asyncio
    async def test_upload_success(self, client: AsyncClient):
        """上传一条广告，返回 ad_id"""
        resp = await client.post("/api/v1/ads/upload", json={
            "title": "测试运动鞋",
            "provider": "Nike",
            "ad_text": "这是一双很舒适的运动鞋",
            "ad_images": ["https://example.com/img.jpg"],
            "tab": "featured",
        })
        assert resp.status_code == 200
        data = resp.json()
        assert data["code"] == 0
        assert data["dup"] is False
        # ad_id 应为有效 UUID
        uuid.UUID(data["ad_id"])

    @pytest.mark.asyncio
    async def test_upload_dedup(self, client: AsyncClient):
        """重复上传相同内容返回 dup=True"""
        ad_data = {"title": "测试商品", "provider": "品牌A", "ad_text": "描述文字"}

        # 第一次上传
        resp1 = await client.post("/api/v1/ads/upload", json=ad_data)
        assert resp1.status_code == 200
        assert resp1.json()["dup"] is False

        # 第二次上传完全相同内容
        resp2 = await client.post("/api/v1/ads/upload", json=ad_data)
        assert resp2.status_code == 200
        assert resp2.json()["dup"] is True
        assert resp2.json()["ad_id"] == resp1.json()["ad_id"]

    @pytest.mark.asyncio
    async def test_feed_returns_items(self, client: AsyncClient):
        """Feed 接口返回已上传的广告"""
        # 上传 3 条广告
        for i in range(3):
            await client.post("/api/v1/ads/upload", json={
                "title": f"广告 {i}",
                "provider": f"品牌 {i}",
                "ad_text": f"这是第 {i} 条广告",
                "tab": "featured",
            })

        # 取 Feed
        resp = await client.get("/api/v1/ads/feed", params={"tab": "featured"})
        assert resp.status_code == 200
        data = resp.json()
        assert len(data["items"]) == 3
        # 验证字段完整性
        for item in data["items"]:
            assert "id" in item
            assert "title" in item
            assert "provider" in item
            assert "ad_text" in item
            assert "tab" in item

    @pytest.mark.asyncio
    async def test_feed_cursor_pagination(self, client: AsyncClient):
        """cursor 分页：有下一页时返回 next_cursor"""
        # 上传 25 条（超过默认 size=20）
        for i in range(25):
            await client.post("/api/v1/ads/upload", json={
                "title": f"分页测试 {i}",
                "provider": "品牌",
                "ad_text": f"测试内容 {i}",
                "tab": "featured",
            })

        resp = await client.get("/api/v1/ads/feed", params={"tab": "featured", "size": 10})
        assert resp.status_code == 200
        data = resp.json()
        assert len(data["items"]) == 10
        assert data["next_cursor"] is not None  # 还有更多

        # 第二页
        resp2 = await client.get("/api/v1/ads/feed", params={
            "tab": "featured",
            "size": 10,
            "cursor": data["next_cursor"],
        })
        assert resp2.status_code == 200
        data2 = resp2.json()
        assert len(data2["items"]) >= 1
        assert data2.get("next_cursor") is not None  # 还有第三页

    @pytest.mark.asyncio
    async def test_feed_respects_tab(self, client: AsyncClient):
        """Feed 按 Tab 过滤"""
        await client.post("/api/v1/ads/upload", json={
            "title": "电商广告", "provider": "品牌", "ad_text": "内容", "tab": "ecommerce",
        })
        await client.post("/api/v1/ads/upload", json={
            "title": "本地广告", "provider": "品牌", "ad_text": "内容", "tab": "local",
        })

        resp_featured = await client.get("/api/v1/ads/feed", params={"tab": "featured"})
        assert resp_featured.status_code == 200
        assert len(resp_featured.json()["items"]) == 0

        resp_local = await client.get("/api/v1/ads/feed", params={"tab": "local"})
        assert resp_local.status_code == 200
        assert len(resp_local.json()["items"]) == 1


class TestAdDetail:
    """广告详情"""

    @pytest.mark.asyncio
    async def test_detail_existing(self, client: AsyncClient):
        """存在的广告返回详情（直接返回 AdItem）"""
        upload_resp = await client.post("/api/v1/ads/upload", json={
            "title": "详情测试", "provider": "品牌", "ad_text": "详细内容",
        })
        ad_id = upload_resp.json()["ad_id"]

        resp = await client.get(f"/api/v1/ads/{ad_id}")
        assert resp.status_code == 200
        data = resp.json()
        assert data["title"] == "详情测试"

    @pytest.mark.asyncio
    async def test_detail_not_found(self, client: AsyncClient):
        """不存在��广告返回 404"""
        resp = await client.get(f"/api/v1/ads/{uuid.uuid4()}")
        assert resp.status_code == 404


class TestLike:
    """点赞/取消赞"""

    @pytest.mark.asyncio
    async def test_like_and_unlike(self, client: AsyncClient):
        """点赞后再次点赞取消"""
        upload_resp = await client.post("/api/v1/ads/upload", json={
            "title": "点赞测试", "provider": "品牌", "ad_text": "内容",
        })
        ad_id = upload_resp.json()["ad_id"]

        # 点赞
        resp1 = await client.post(
            f"/api/v1/ads/{ad_id}/like",
            headers={"X-Device-ID": "device_001"},
        )
        assert resp1.status_code == 200

        # 再次点赞（取消）
        resp2 = await client.post(
            f"/api/v1/ads/{ad_id}/like",
            headers={"X-Device-ID": "device_001"},
        )
        assert resp2.status_code == 200

    @pytest.mark.asyncio
    async def test_like_missing_device_id(self, client: AsyncClient):
        """无 device_id 返回 400"""
        upload_resp = await client.post("/api/v1/ads/upload", json={
            "title": "点赞测试2", "provider": "品牌", "ad_text": "内容",
        })
        ad_id = upload_resp.json()["ad_id"]

        resp = await client.post(f"/api/v1/ads/{ad_id}/like")
        assert resp.status_code == 400


class TestAnalytics:
    """埋点上报"""

    @pytest.mark.asyncio
    async def test_report_events(self, client: AsyncClient):
        """批量上报埋点事件"""
        import time

        resp = await client.post("/api/v1/analytics/event", json={
            "events": [
                {
                    "device_id": "device_001",
                    "ad_id": str(uuid.uuid4()),
                    "event_type": "impression",
                    "event_time": int(time.time() * 1000),
                },
                {
                    "device_id": "device_001",
                    "ad_id": str(uuid.uuid4()),
                    "event_type": "click",
                    "event_time": int(time.time() * 1000),
                },
            ],
        })
        assert resp.status_code == 200
        assert resp.json()["code"] == 0


class TestHealth:
    """健康检查"""

    @pytest.mark.asyncio
    async def test_health_reports_ok(self, client: AsyncClient):
        """有 DB (SQLite) 和 Redis (fakeredis) 时应返回 ok"""
        resp = await client.get("/health")
        assert resp.status_code == 200
        data = resp.json()
        assert data["status"] == "ok"
        assert data["db"] == "ok"
        assert data["redis"] == "ok"


class TestErrorHandling:
    """统一错误处理"""

    @pytest.mark.asyncio
    async def test_validation_error_format(self, client: AsyncClient):
        """校验失败返回 422 + 标准格式"""
        resp = await client.post("/api/v1/ads/upload", json={"title": "no other fields"})
        assert resp.status_code == 422
        data = resp.json()
        assert data["code"] == 422
        assert "detail" in data

    @pytest.mark.asyncio
    async def test_invalid_uuid_detail(self, client: AsyncClient):
        """无效 UUID 应返回 422（Pydantic 校验）"""
        resp = await client.get("/api/v1/ads/not-a-uuid")
        assert resp.status_code == 422
