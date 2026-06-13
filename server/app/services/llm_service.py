"""LLM Client 封装：支持 OpenAI 兼容 API"""

import json
import time
from typing import Optional

from openai import OpenAI

from app.config import settings


class LLMClient:
    """统一的 LLM 调用封装"""

    def __init__(self):
        self.client = OpenAI(
            api_key=settings.llm_api_key,
            base_url=settings.llm_base_url,
            timeout=settings.llm_request_timeout,
            max_retries=2,
        )
        self.model = settings.llm_model

    def chat(
        self,
        prompt: str,
        system: str = "你是一个广告内容分析助手。",
        response_format: Optional[dict] = None,
    ) -> str:
        """调用 LLM chat completion，返回文本内容"""
        kwargs = {
            "model": self.model,
            "messages": [
                {"role": "system", "content": system},
                {"role": "user", "content": prompt},
            ],
            "temperature": 0.3,
        }
        if response_format:
            kwargs["response_format"] = response_format

        response = self.client.chat.completions.create(**kwargs)
        return response.choices[0].message.content or ""

    def chat_with_json_schema(
        self,
        prompt: str,
        schema: dict,
        schema_name: str = "output",
        system: str = "你是一个广告内容分析助手。",
    ) -> dict:
        """调用 LLM 并约束返回 JSON。使用 json_object 兼容 DeepSeek 等不支持 json_schema 的 API。"""
        import json as _json
        schema_desc = _json.dumps(schema, ensure_ascii=False)
        full_prompt = f"{prompt}\n\n请严格按照以下 JSON 格式返回，不要包含其他内容：\n{schema_desc}"
        content = self.chat(
            prompt=full_prompt,
            system=system,
            response_format={"type": "json_object"},
        )
        return _json.loads(content)

    def extract_intent(self, query: str) -> list[str]:
        """意图识别：从自然语言中提取用于广告标签匹配的关键词"""
        prompt = f"""分析用户搜索意图，提取3-8个可用于匹配广告标签的关键词。

要求：
1. 关键词应覆盖：品类词（如运动鞋、T恤）、品牌名（如Nike）、风格属性（如休闲、高端）、受众（如学生党）
2. 不要只从原文复制词汇，要推断用户真实意图并扩展同义词
3. 优先使用具体品类名而非泛称（如用"运动鞋/跑鞋"而非仅仅"鞋子"）

用户搜索：{query}

只返回 JSON 对象，格式：{{"keywords": ["词1", "词2", ...]}}"""

        try:
            result = self.chat_with_json_schema(
                prompt=prompt,
                schema={
                    "type": "object",
                    "properties": {
                        "keywords": {
                            "type": "array",
                            "items": {"type": "string"},
                            "description": "提取的关键词标签列表",
                        },
                    },
                    "required": ["keywords"],
                    "additionalProperties": False,
                },
                schema_name="intent_keywords",
            )
            return result.get("keywords", [])
        except (json.JSONDecodeError, KeyError):
            return [w.strip() for w in query.split() if len(w.strip()) >= 1]

    def rerank(
        self,
        query: str,
        candidates: list[dict],
        score_threshold: float = 0.6,
    ) -> list[dict]:
        """LLM 对每条候选独立打分(0.0-1.0)，按分数排序，低于阈值过滤"""
        if not candidates:
            return []

        candidates_text = "\n---\n".join(
            f"[{i}] 标题: {c.get('title', '')} | 摘要: {c.get('ai_summary', '')} | 标签: {c.get('ai_tags', [])}"
            for i, c in enumerate(candidates)
        )

        prompt = f"""用户搜索: {query}

对以下每条广告评估与搜索意图的相关程度，给出0.0-1.0分的相关性评分：
- 1.0: 完全匹配用户需求
- 0.5: 部分相关
- 0.0: 完全无关

候选广告:
{candidates_text}

对每条广告独立打分，返回 JSON 对象，格式: {{"scores": {{"0": 0.85, "1": 0.12, ...}}}}"""

        try:
            result = self.chat_with_json_schema(
                prompt=prompt,
                schema={
                    "type": "object",
                    "properties": {
                        "scores": {
                            "type": "object",
                            "description": "索引→分数的映射",
                        },
                    },
                    "required": ["scores"],
                    "additionalProperties": False,
                },
                schema_name="rerank_scores",
            )
            scores = result.get("scores", {})
            # 解析分数、过滤、排序
            scored = []
            for idx_str, score in scores.items():
                try:
                    idx = int(idx_str)
                    s = float(score)
                    if 0 <= idx < len(candidates) and s >= score_threshold:
                        candidates[idx]["score"] = s
                        scored.append(candidates[idx])
                except (ValueError, TypeError):
                    continue
            scored.sort(key=lambda c: c.get("score", 0), reverse=True)
            return scored
        except (json.JSONDecodeError, KeyError, Exception):
            return [c for c in candidates if c.get("similarity", 0) >= 0.3]

    def generate_summary_and_tags(self, title: str, ad_text: str) -> dict:
        """为广告生成 AI 摘要和智能标签（供 Celery 任务使用）"""
        prompt = f"""请为以下广告生成摘要(20-50字)和智能标签:

标题: {title}
内容: {ad_text}

要求：
- 摘要：20-50字高度凝练的广告短语
- 标签：提取多维度标签，每个标签包含 category（品类/风格/受众/场景）和 value（标签值）"""

        schema = {
            "type": "object",
            "properties": {
                "summary": {"type": "string", "description": "20-50字广告摘要"},
                "tags": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {
                            "category": {"type": "string", "description": "标签类别"},
                            "value": {"type": "string", "description": "标签值"},
                        },
                        "required": ["category", "value"],
                        "additionalProperties": False,
                    },
                },
            },
            "required": ["summary", "tags"],
            "additionalProperties": False,
        }

        return self.chat_with_json_schema(prompt=prompt, schema=schema, schema_name="ad_summary_tags")

    def check_synonym(self, new_tag: str, existing_tag: str, ad_text: str) -> bool:
        """LLM 二分类判断两个标签是否同义"""
        prompt = f"""判断以下两个标签是否是同义词（概念相同）：

标签1: "{new_tag}"
标签2: "{existing_tag}"
广告上下文: {ad_text}

只返回 JSON：{{"is_synonym": true/false}}"""

        try:
            result = self.chat_with_json_schema(
                prompt=prompt,
                schema={
                    "type": "object",
                    "properties": {
                        "is_synonym": {"type": "boolean"},
                    },
                    "required": ["is_synonym"],
                    "additionalProperties": False,
                },
                schema_name="synonym_check",
            )
            return result.get("is_synonym", False)
        except (json.JSONDecodeError, KeyError):
            return False


# 全局 LLM 客户端单例
llm_client = LLMClient()
