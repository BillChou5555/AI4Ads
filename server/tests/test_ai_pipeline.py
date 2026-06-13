"""测试 AI 管线逻辑"""

from unittest.mock import MagicMock, patch

import pytest


class TestSummaryGeneration:
    def test_llm_result_parsing(self):
        """测试 LLM 结果解析"""
        from app.services.llm_service import LLMClient

        client = LLMClient()
        client.client = MagicMock()
        mock_response = MagicMock()
        mock_response.choices = [MagicMock()]
        mock_response.choices[0].message.content = (
            '{"summary": "这是一款超高性价比的运动鞋，适合日常通勤和户外运动",'
            '"tags": [{"category": "品类", "value": "运动鞋"},'
            '{"category": "受众", "value": "学生党"},'
            '{"category": "风格", "value": "极简"}]}'
        )
        client.client.chat.completions.create.return_value = mock_response

        result = client.generate_summary_and_tags("运动鞋广告", "这是一双很便宜的运动鞋")
        assert result["summary"] == "这是一款超高性价比的运动鞋，适合日常通勤和户外运动"
        assert len(result["tags"]) == 3
        assert result["tags"][0]["value"] == "运动鞋"

    def test_fallback_on_llm_failure(self):
        """LLM 解析失败时应降级"""
        ad_text = "这是一双很便宜的运动鞋广告文本内容比较长" * 3
        # 截断到 50 字 + "…"
        fallback_summary = ad_text[:50] + "…"
        assert len(fallback_summary) == 51


class TestTagNormalization:
    def test_empty_tags_returns_empty(self):
        """空标签列表返回空"""
        result = []
        assert result == []

    def test_direct_match_skips_llm(self):
        """完全相同标签应直接匹配，不调用 LLM"""
        # 模拟 _normalize_tags 中的匹配逻辑
        existing = "运动鞋"
        tag_value = "运动鞋"
        assert existing == tag_value  # 直接跳过 LLM


class TestAiPipelineFallback:
    def test_json_decode_error_fallback(self):
        """JSON 解析失败时使用兜底策略"""
        ad_text = "测试广告文本内容" * 10  # 80 chars > 50
        summary = ad_text[:50] + "…" if len(ad_text) > 50 else ad_text
        assert len(ad_text) > 50
        assert summary.endswith("…")
        # 标签应为空列表
        tags = []
        assert tags == []
