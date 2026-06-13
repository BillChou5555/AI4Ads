"""测试配置加载"""


def test_config_loads():
    """验证配置能正常加载"""
    from app.config import settings

    assert settings.llm_model == "gpt-4o"
    assert settings.embedding_dim == 1536
    assert settings.feed_cache_ttl == 300
    assert settings.llm_request_timeout == 30
