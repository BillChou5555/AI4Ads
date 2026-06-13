"""Celery 应用定义"""

from celery import Celery
from app.config import settings

celery_app = Celery(
    "aiads",
    broker=settings.celery_broker_url,
    include=[
        "app.tasks.ai_pipeline",
        "app.tasks.feed_cache_warmer",
    ],
)

celery_app.conf.update(
    task_serializer="json",
    accept_content=["json"],
    result_serializer="json",
    timezone="Asia/Shanghai",
    enable_utc=True,
    task_track_started=True,
    task_acks_late=True,
    worker_prefetch_multiplier=1,
)
