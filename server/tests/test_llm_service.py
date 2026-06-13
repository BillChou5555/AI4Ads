"""测试 LLM 服务（使用 mock，不调用真实 API）"""

import json
from unittest.mock import MagicMock, patch

import pytest

from app.services.llm_service import LLMClient


class TestChatWithJsonSchema:
    def test_parses_valid_json_response(self):
        client = LLMClient()
        client.client = MagicMock()
        mock_response = MagicMock()
        mock_response.choices = [MagicMock()]
        mock_response.choices[0].message.content = json.dumps({"keywords": ["运动鞋", "性价比"]})
        client.client.chat.completions.create.return_value = mock_response

        schema = {
            "type": "object",
            "properties": {"keywords": {"type": "array", "items": {"type": "string"}}},
            "required": ["keywords"],
            "additionalProperties": False,
        }

        result = client.chat_with_json_schema("test prompt", schema)
        assert result == {"keywords": ["运动鞋", "性价比"]}

    def test_handles_invalid_json(self):
        client = LLMClient()
        client.client = MagicMock()
        mock_response = MagicMock()
        mock_response.choices = [MagicMock()]
        mock_response.choices[0].message.content = "not json"
        client.client.chat.completions.create.return_value = mock_response

        with pytest.raises(json.JSONDecodeError):
            client.chat_with_json_schema("test", {"type": "object", "properties": {}})


class TestExtractIntent:
    def test_returns_keywords(self):
        client = LLMClient()
        client.client = MagicMock()
        mock_response = MagicMock()
        mock_response.choices = [MagicMock()]
        mock_response.choices[0].message.content = json.dumps({"keywords": ["运动", "低价", "鞋"]})
        client.client.chat.completions.create.return_value = mock_response

        result = client.extract_intent("我想找便宜的运动鞋")
        assert result == ["运动", "低价", "鞋"]

    def test_falls_back_on_failure(self):
        client = LLMClient()
        client.client = MagicMock()
        mock_response = MagicMock()
        mock_response.choices = [MagicMock()]
        mock_response.choices[0].message.content = "bad response"
        client.client.chat.completions.create.return_value = mock_response

        result = client.extract_intent("运动鞋 便宜")
        # 降级为按空格分词
        assert len(result) >= 1


class TestRerank:
    def test_returns_ranked_list(self):
        client = LLMClient()
        client.client = MagicMock()
        mock_response = MagicMock()
        mock_response.choices = [MagicMock()]
        mock_response.choices[0].message.content = json.dumps({"ranked_indices": [1, 0, 2]})
        client.client.chat.completions.create.return_value = mock_response

        candidates = [
            {"id": "a", "title": "first", "ai_summary": "summary a", "ai_tags": []},
            {"id": "b", "title": "second", "ai_summary": "summary b", "ai_tags": []},
            {"id": "c", "title": "third", "ai_summary": "summary c", "ai_tags": []},
        ]

        result = client.rerank("query", candidates, top_n=2)
        assert len(result) == 2
        assert result[0]["id"] == "b"
        assert result[1]["id"] == "a"

    def test_empty_candidates(self):
        client = LLMClient()
        result = client.rerank("query", [])
        assert result == []

    def test_falls_back_on_failure(self):
        client = LLMClient()
        client.client = MagicMock()
        mock_response = MagicMock()
        mock_response.choices = [MagicMock()]
        mock_response.choices[0].message.content = "bad"
        client.client.chat.completions.create.return_value = mock_response

        candidates = [{"id": "a", "title": "t", "ai_summary": "s", "ai_tags": []}]
        result = client.rerank("query", candidates, top_n=1)
        assert len(result) == 1


class TestCheckSynonym:
    def test_returns_true_for_synonym(self):
        client = LLMClient()
        client.client = MagicMock()
        mock_response = MagicMock()
        mock_response.choices = [MagicMock()]
        mock_response.choices[0].message.content = json.dumps({"is_synonym": True})
        client.client.chat.completions.create.return_value = mock_response

        result = client.check_synonym("运动鞋", "跑鞋", "运动鞋广告内容")
        assert result is True

    def test_returns_false_for_different(self):
        client = LLMClient()
        client.client = MagicMock()
        mock_response = MagicMock()
        mock_response.choices = [MagicMock()]
        mock_response.choices[0].message.content = json.dumps({"is_synonym": False})
        client.client.chat.completions.create.return_value = mock_response

        result = client.check_synonym("运动鞋", "高跟鞋", "运动鞋广告内容")
        assert result is False
