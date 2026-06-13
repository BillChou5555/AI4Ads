"""测试种子数据脚本"""

import pytest


def test_all_required_fields():
    """验证种子数据包含所有必填字段"""
    from app.seed import SAMPLE_ADS

    assert len(SAMPLE_ADS) == 16
    for ad in SAMPLE_ADS:
        assert ad["title"], f"title 缺失"
        assert ad["provider"], f"provider 缺失: {ad.get('title')}"
        assert ad["ad_text"], f"ad_text 缺失: {ad['title']}"
        assert ad["tab"] in ("featured", "ecommerce", "local"), f"无效 tab: {ad['title']}"


def test_content_hash_unique():
    """验证种子数据无重复"""
    from app.seed import SAMPLE_ADS, compute_hash

    seen = set()
    for ad in SAMPLE_ADS:
        h = compute_hash(ad["title"], ad["provider"], ad["ad_text"])
        assert h not in seen, f"重复广告: {ad['title']}"
        seen.add(h)


def test_ai_data_has_matches():
    """验证种子数据的 AI 摘要能匹配到对应广告"""
    from app.seed import SAMPLE_ADS, match_ai_data

    matched = 0
    for ad in SAMPLE_ADS:
        ai = match_ai_data(ad["title"])
        if ai:
            matched += 1
            assert len(ai["summary"]) > 0
            assert len(ai["tags"]) >= 1

    assert matched >= len(SAMPLE_ADS), f"只有 {matched} 条匹配到 AI 数据"


def test_tab_distribution():
    """验证种子数据覆盖所有 Tab"""
    from app.seed import SAMPLE_ADS

    tabs = {ad["tab"] for ad in SAMPLE_ADS}
    assert "featured" in tabs
    assert "ecommerce" in tabs
    assert "local" in tabs
