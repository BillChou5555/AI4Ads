"""请求日志中间件"""

import logging
import time

from fastapi import Request

logger = logging.getLogger("api.access")


async def request_logger_middleware(request: Request, call_next):
    start = time.time()
    response = await call_next(request)
    duration_ms = (time.time() - start) * 1000

    logger.info(
        "%s %s → %s (%.2fms)",
        request.method,
        request.url.path,
        response.status_code,
        duration_ms,
    )
    return response
