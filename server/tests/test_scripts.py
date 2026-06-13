"""测试 scripts 目录下的数据文件和脚本"""

import json
import sys
from pathlib import Path


def test_sample_ads_json_valid():
    """验证 sample_ads.json 格式正确"""
    json_path = Path(__file__).parent.parent / "scripts" / "sample_ads.json"
    assert json_path.exists(), f"JSON 文件不存在: {json_path}"

    with open(json_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    assert isinstance(data, list), "JSON 顶层应为数组"
    assert len(data) == 16, f"期望 16 条，实际 {len(data)} 条"

    required_fields = {"title", "provider", "ad_text"}
    valid_tabs = {"featured", "ecommerce", "local"}

    for i, ad in enumerate(data):
        missing = required_fields - set(ad.keys())
        assert not missing, f"第 {i+1} 条缺少字段: {missing}"
        assert ad["title"], f"第 {i+1} 条 title 为空"
        assert ad["provider"], f"第 {i+1} 条 provider 为空"
        assert ad["ad_text"], f"第 {i+1} 条 ad_text 为空"
        assert ad.get("tab", "featured") in valid_tabs, f"第 {i+1} 条 tab 无效"


def test_bulk_upload_importable():
    """验证批量上传脚本可导入"""
    scripts_dir = Path(__file__).parent.parent / "scripts"
    sys.path.insert(0, str(scripts_dir))
    import bulk_upload
    assert hasattr(bulk_upload, "upload_file")
    sys.path.pop(0)
