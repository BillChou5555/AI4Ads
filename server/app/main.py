"""FastAPI 应用入口"""

import logging
from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.api.v1 import ads, search
from app.core.database import engine, get_db
from app.core.redis_client import get_redis

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s: %(message)s",
)


@asynccontextmanager
async def lifespan(application: FastAPI):
    yield


app = FastAPI(
    title="AI Ads Feed API",
    version="0.1.0",
    lifespan=lifespan,
)


# -- 请求日志中间件 --

@app.middleware("http")
async def log_requests(request: Request, call_next):
    from app.middleware.logger import request_logger_middleware
    return await request_logger_middleware(request, call_next)


# -- 全局异常处理 --

@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError):
    return JSONResponse(
        status_code=422,
        content={"code": 422, "detail": exc.errors()},
    )


@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    logging.getLogger("api.error").error("Unhandled error: %s", exc, exc_info=True)
    return JSONResponse(
        status_code=500,
        content={"code": 500, "detail": "internal server error"},
    )


# -- 路由注册 --

app.include_router(search.router, prefix="/api/v1")
app.include_router(ads.router, prefix="/api/v1")


@app.get("/health")
async def health_check(
    db=Depends(get_db),
    redis=Depends(get_redis),
):
    db_ok = False
    redis_ok = False

    try:
        await db.execute(__import__("sqlalchemy").text("SELECT 1"))
        db_ok = True
    except Exception:
        pass

    try:
        await redis.ping()
        redis_ok = True
    except Exception:
        pass

    status = "ok" if (db_ok and redis_ok) else "degraded"
    return {
        "status": status,
        "db": "ok" if db_ok else "error",
        "redis": "ok" if redis_ok else "error",
    }
