# AI 智能广告信息流 — 服务端

## 技术栈

| 类别 | 选型 |
|------|------|
| 语言 | Python 3.11+ |
| Web 框架 | FastAPI (uvicorn) |
| 数据库 | PostgreSQL 16 + pgvector |
| 缓存 | Redis 7 |
| 异步任务 | Celery (Redis broker) |
| 向量检索 | pgvector ivfflat (cosine) |
| LLM | OpenAI 兼容 API（DeepSeek / GPT-4o 等） |
| Embedding | BAAI/bge-large-zh-v1.5（本地 CPU, 1024d） |
| 部署 | Docker Compose |

## 快速启动

### 1. 配置环境变量

```bash
cp .env.example .env
# 编辑 .env，填入有效的 LLM_API_KEY
```

### 2. 启动全部服务

```bash
docker compose up -d
```

### 3. 初始化数据库

```bash
# 执行数据库迁移（建表 + pgvector 扩展）
docker compose exec api alembic upgrade head

# 从 ads.json 导入种子数据（100 条广告，含 AI 摘要和标签）
docker compose exec api python scripts/import_ads.py scripts/ads.json --clear
```

### 4. 验证

```bash
curl http://localhost:8000/health
# {"status":"ok","db":"ok","redis":"ok"}

curl http://localhost:8000/api/v1/ads/feed?tab=featured
```

### 本地开发（仅启动 PostgreSQL + Redis）

```bash
docker compose up -d postgres redis
alembic upgrade head
python -m app.seed
uvicorn app.main:app --reload --port 8000
```

## 项目结构

```
server/
├── app/
│   ├── main.py                    # FastAPI 入口 + 全局异常处理 + 健康检查
│   ├── config.py                  # 环境变量配置（Pydantic Settings）
│   ├── core/
│   │   ├── database.py            # SQLAlchemy 异步引擎 + 连接池
│   │   ├── redis_client.py        # Redis 异步客户端
│   │   └── celery_app.py          # Celery 应用定义
│   ├── models/
│   │   └── ad.py                  # 广告 ORM 模型（含 pgvector embedding）
│   ├── schemas/
│   │   ├── ad.py                  # 上传 / Feed / 详情 / 点赞 Schema
│   │   └── search.py              # 搜索 Schema
│   ├── api/
│   │   ├── deps.py                # 依赖注入（get_device_id 等）
│   │   └── v1/
│   │       ├── ads.py             # 物料入库 / Feed 分发 / 详情 / 点赞 / 搜索
│   │       └── search.py          # RAG 自然语言搜索
│   ├── services/
│   │   ├── ad_service.py          # 广告 CRUD + 分页 + 曝光排重
│   │   ├── search_service.py      # RAG 搜索编排（向量粗筛 → LLM 精排）
│   │   ├── llm_service.py         # LLM Client 封装（意图识别 + 打分 + 摘要生成）
│   │   ├── embedding_service.py   # BGE Embedding（Redis 缓存 + 懒加载）
│   │   └── vector_store.py        # pgvector 操作（余弦相似度检索）
│   ├── tasks/
│   │   ├── ai_pipeline.py         # Celery：AI 摘要 + 标签生成 + embedding 入库
│   │   └── feed_cache_warmer.py   # Celery Beat：Feed 首页缓存预热
│   └── middleware/
│       ├── logger.py              # 请求日志
│       └── ratelimit.py           # 滑动窗口限流
├── scripts/
│   ├── ads.json                   # 100 条种子广告数据
│   └── import_ads.py              # JSON 直接入库脚本（含去重 + 统计）
├── alembic/
│   ├── env.py                     # 异步迁移环境
│   └── versions/                  # 迁移脚本
├── tests/                         # 单元测试 + 集成测试
├── docker-compose.yml
├── Dockerfile
└── requirements.txt
```

## API 列表

| 方法 | 路径 | 描述 |
|------|------|------|
| `GET` | `/health` | 健康检查 |
| `GET` | `/api/v1/ads/feed` | Feed 分发（cursor 分页 + 曝光排重） |
| `GET` | `/api/v1/ads/search` | RAG 自然语言搜索 |
| `GET` | `/api/v1/ads/{ad_id}` | 广告详情 |
| `GET` | `/api/v1/ads/export` | 导出广告库（JSON，支持按 Tab 过滤） |
| `POST` | `/api/v1/ads/upload` | 物料入库（content_hash 去重 → 触发 AI 管线） |
| `POST` | `/api/v1/ads/{ad_id}/like` | 点赞 / 取消赞 |
| `DELETE` | `/api/v1/ads/{ad_id}` | 删除广告（硬删除） |

---

## API 接口实现详解

### 1. 健康检查 `GET /health`

**实现文件**：`app/main.py`

同时检测 PostgreSQL（`SELECT 1`）和 Redis（`PING`）的连通性。任一服务不可用时返回 `"degraded"` 而非 500，确保 Load Balancer 能区分"服务活着但依赖挂了"和"服务崩溃"。

**响应示例**：
```json
{"status":"ok","db":"ok","redis":"ok"}
```

**请求示例**：
```bash
curl http://localhost:8000/health
```

---

### 2. Feed 分发 `GET /api/v1/ads/feed`

**实现文件**：`app/api/v1/ads.py` → `app/services/ad_service.py`

**分页策略**：cursor-based 分页，cursor 编码为 `"{created_at}|{ad_id}"`。每次查询取 `size + 1` 条记录，多取的一条用于判断 `has_more`，不返回给客户端。排序方式为 `created_at DESC, id DESC`，id 作为 tiebreaker 保证同秒入库的广告不会重复或遗漏。

**缓存策略**：仅首页（`cursor` 为空时）走 Redis 缓存，key 为 `feed:{tab}:home`，TTL 由 `FEED_CACHE_TTL` 环境变量控制（默认 300 秒）。Celery Beat 定时任务 `feed_cache_warmer` 每 2 分钟预热缓存，保证用户首屏始终从 Redis 命中的速度。翻页请求不走缓存——直接查 PostgreSQL，保证数据实时性。

**曝光排重**：基于 `X-Device-ID` 请求头（兼容 query 参数 `device_id`）。Redis Set `viewed:{device_id}` 记录该设备已曝光的广告 ID。Feed 返回前过滤掉已曝光广告；同时提供 `mark_viewed()` 方法供客户端调用（目前客户端 M10 改为本地记录，服务端排重仍保留作为兜底）。Set 过期时间 24 小时。

**请求示例**：
```bash
# 首页
curl "http://localhost:8000/api/v1/ads/feed?tab=featured&size=20"

# 翻页
curl "http://localhost:8000/api/v1/ads/feed?tab=featured&size=20&cursor=2026-06-01T00:00:00|uuid"

# 带设备 ID（曝光排重）
curl -H "X-Device-ID: device_001" "http://localhost:8000/api/v1/ads/feed?tab=featured"
```

---

### 3. RAG 自然语言搜索 `GET /api/v1/ads/search`（重点）

**实现文件**：`app/api/v1/search.py` → `app/services/search_service.py`

这是整个服务端最复杂的链路。搜索请求经过**四阶段流水线**，每个阶段都有独立的降级策略：

```
用户输入："我想找一双性价比高的运动鞋"
    │
    ▼
┌─────────────────────────────────────────────────────┐
│ Step 1: BGE Embedding 向量化                          │
│   "我想找一双性价比高的运动鞋" → 1024 维向量           │
│   ── 失败时跳过，进入 Step 3 纯关键词匹配             │
└─────────────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────────────┐
│ Step 2: pgvector Cosine 相似度粗筛（IVFFlat 索引）     │
│   SELECT ... WHERE 1 - (embedding <=> :vec) >= 0.2   │
│   ORDER BY embedding <=> :vec LIMIT 50               │
│   阈值 0.2：过滤噪声，保留 Top-50 候选                │
│   ── 失败时 candidates 为空，进入 Step 3             │
└─────────────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────────────┐
│ Step 3: LLM 意图识别 + 关键词补召（仅在候选不足时触发）│
│   ① LLM 提取关键词：["运动鞋", "跑步鞋", "性价比", …]  │
│   ② pg_trgm 标签模糊匹配：                             │
│      SELECT ... WHERE ai_tags ILIKE '%运动鞋%'        │
│   ③ 去重合并：key=ad_id，确保不与 Step 2 重复         │
│   ── 失败时用原始 query 分词作为 fallback 关键词      │
└─────────────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────────────┐
│ Step 4: LLM 独立打分精排                              │
│   将候选列表 + 用户 query 发给 LLM                     │
│   LLM 对每条广告给出 0.0-1.0 相关性评分                │
│   阈值 0.6：过滤低相关度结果                           │
│   按评分降序排列                                       │
│   ── 失败时降级为 similarity ≥ 0.3 排序              │
└─────────────────────────────────────────────────────┘
    │
    ▼
分页返回（page / page_size）
```

#### 为什么用 LLM 精排而非纯向量相似度？

向量相似度衡量的是**语义距离**，但不理解**用户真实意图**。例如搜索"学生党平价鞋"，向量可能会把包含"学生"、"平价"、"鞋"的广告都召回，但：

- LLM 能理解"学生党" = 低预算、年轻受众，"平价" = 价格敏感，"鞋" = 具体品类
- 两条相似度都是 0.6 的候选，LLM 可以判断一条是"99 元帆布鞋"（高度相关，0.9 分），另一条是"学生宿舍出租"（无关，0.1 分）——纯向量区分不了这种细微差别

#### 为什么用两阶段（0.2 粗筛 + 0.6 精排）而非单阶段？

- 全量广告发给 LLM 逐条打分成本太高（按 token 计费，100 条就是 100 次评估）
- pgvector 0.2 阈值粗筛把候选从数千条砍到 50 条以内，只需几毫秒
- LLM 只对 Top-50 打分，成本固定、延迟可预测

#### 降级链

| 阶段 | 失败处理 |
|------|---------|
| Embedding 向量化 | 跳过 Step 1/2，纯关键词匹配 |
| pgvector 检索 | candidates 为空，触发 Step 3 关键词补召 |
| LLM 意图识别 | 原始 query 分词作为关键词 |
| LLM 精排打分 | 降级为 similarity ≥ 0.3 过滤 + 降序 |

整个链路**任意阶段失败都不会导致搜索返回空**——降级链条保证最终总能回到关键词模糊匹配。

#### Embedding 缓存

`EmbeddingService` 内建 Redis 缓存：key 为 `emb:{model}:{text_hash}`，TTL 1 小时。相同或相似的 query 重复搜索时跳过模型推理，直接返回缓存向量。

**请求示例**：
```bash
curl "http://localhost:8000/api/v1/ads/search?q=性价比高的运动鞋&page=1&page_size=20"
```

---

### 4. 广告详情 `GET /api/v1/ads/{ad_id}`

**实现文件**：`app/api/v1/ads.py` → `app/services/ad_service.py`

直接按 UUID 主键查询，返回完整 AdItem。404 时抛出 HTTPException。

**请求示例**：
```bash
curl http://localhost:8000/api/v1/ads/929f1748-d35b-409c-a070-907e52539c46
```

---

### 5. 导出广告库 `GET /api/v1/ads/export`

**实现文件**：`app/api/v1/ads.py`

按 `import_ads.py` 要求的 JSON 格式导出全部有效广告（`status=1`）。支持按 `tab` 过滤，分批查询（每批 500 条）避免内存溢出。返回 `StreamingResponse` + `Content-Disposition: attachment`，浏览器访问时自动触发文件下载。

导出的 JSON 文件可直接被 `import_ads.py` 重新导入，实现数据迁移。

**请求示例**：
```bash
# 全部导出
curl -O http://localhost:8000/api/v1/ads/export

# 按 Tab 过滤
curl -O "http://localhost:8000/api/v1/ads/export?tab=ecommerce"
```

---

### 6. 物料入库 `POST /api/v1/ads/upload`

**实现文件**：`app/api/v1/ads.py` → `app/services/ad_service.py`

**去重策略**：对 `(title, provider, ad_text)` 计算 MD5 hash。命中 → 返回已有 `ad_id`（`dup: true`），跳过入库和 AI 管线。未命中 → 写入数据库（`status=1`），异步触发 Celery AI 管线。

**AI 管线**（`app/tasks/ai_pipeline.py`，Celery 异步执行）：

```
入库成功
  └→ Celery Task: process_ad_summary(ad_id)
       ├── LLM 生成 AI 摘要（20-50 字）
       ├── LLM 提取智能标签 [{category, value}, ...]
       ├── 标签语义去重：
       │     └→ BGE Embedding → pgvector Cosine > 0.85 → LLM 判断同义
       └── BGE Embedding 生成 1024 维向量 → 写入 pgvector 列
```

**标签语义去重原理**：新标签"跑步鞋" → BGE 向量 → pgvector 检索已有标签中 Cosine > 0.85 的候选（如"跑鞋"）→ LLM 二分类判断是否同义 → 同义则复用已有标签名，不同义则新增。防止"跑步鞋""跑鞋""运动跑鞋"在系统中变成三个不同标签。

**降级策略**：
- Celery broker 不可用 → 入库返回成功，跳过 AI 管线，日志 warn
- LLM API 不可用 → 摘要回退为 ad_text 截断前 50 字，标签为空
- Embedding 模型加载失败 → 搜索降级为纯关键词匹配

**请求示例**：
```bash
curl -X POST http://localhost:8000/api/v1/ads/upload \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Nike Air Max 270",
    "provider": "Nike官方旗舰店",
    "ad_text": "全新气垫科技，极致缓震体验，限时8折优惠",
    "ad_images": ["https://example.com/img.jpg"],
    "ad_video_audio": "https://example.com/video.mp4",
    "tab": "ecommerce"
  }'
# {"code": 0, "ad_id": "uuid-here", "dup": false}
```

---

### 7. 点赞 / 取消赞 `POST /api/v1/ads/{ad_id}/like`

**实现文件**：`app/api/v1/ads.py` → `app/services/ad_service.py`

基于 Redis Set 实现，无需写 PostgreSQL。两个 key：
- `likes:{device_id}`：记录该设备点赞过的广告
- `ad_liked_by:{ad_id}`：记录点赞该广告的设备列表

`SISMEMBER` 判断是否已赞 → 已赞则 `SREM`（取消），未赞则 `SADD`（点赞）。设备 ID 通过 `X-Device-ID` 请求头传入，缺失时返回 400。

设计为 Redis-only 而非写 DB 的原因：点赞是高频、低价值操作，Redis Set O(1) 查询 + O(1) 写入，不需要持久化（30 天 TTL）。

**请求示例**：
```bash
curl -X POST http://localhost:8000/api/v1/ads/929f1748-d35b-409c-a070-907e52539c46/like \
  -H "X-Device-ID: device_001"
```

---

### 8. 删除广告 `DELETE /api/v1/ads/{ad_id}`

**实现文件**：`app/api/v1/ads.py` → `app/services/ad_service.py`

硬删除：从 PostgreSQL 中删除广告行（pgvector embedding 一并移除）。404 如果广告不存在。客户端应避免缓存已删除广告的 id。

**请求示例**：
```bash
curl -X DELETE http://localhost:8000/api/v1/ads/929f1748-d35b-409c-a070-907e52539c46
# {"code": 0, "detail": "deleted"}
```

---

## 数据初始化

### 从 ads.json 导入（推荐）

```bash
# 从默认数据文件初始化（100 条广告，含 AI 摘要和标签）
docker compose exec api python scripts/import_ads.py scripts/ads.json --clear
```

`--clear` 清空旧数据后导入。不带此参数则为增量导入（content_hash 去重自动跳过已存在广告）。带 `--overwrite` 则覆盖相同 content_hash 的旧数据。

**ads.json 字段说明**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `title` | string | 是 | 广告标题 |
| `provider` | string | 是 | 品牌 / 广告商 |
| `ad_text` | string | 是 | 广告正文 |
| `tab` | string | 是 | `featured` / `ecommerce` / `local` |
| `ai_summary` | string | 是 | AI 摘要（20-50 字） |
| `ai_tags` | array | 是 | `[{"category": "品类", "value": "运动鞋"}, ...]` |
| `ad_images` | string[] | 否 | 图片 URL 列表，默认 `[]` |
| `ad_video_audio` | string | 否 | 视频 URL，默认 `null` |

### 通过 API 上传（走完整 AI 管线）

适用于没有 AI 摘要 / 标签、需要 LLM 自动生成的场景：

```bash
curl -X POST http://localhost:8000/api/v1/ads/upload \
  -H "Content-Type: application/json" \
  -d '{"title":"...", "provider":"...", "ad_text":"...", "tab":"featured"}'
```

AI 管线在后台由 Celery Worker 异步执行（摘要 + 标签 + embedding），预计 5-15 秒完成。

---

## 架构关键设计

### 去重策略

物料入库前对 `(title, provider, ad_text)` 计算 MD5 hash，命中则跳过入库和 AI 管线，直接返回已有 `ad_id`。保证同一广告不会因重复上传而产生多条记录。

### 分页策略

cursor-based 分页，`created_at DESC` 排序。cursor 编码为 `"{created_at}|{id}"`，id 作为 tiebreaker 避免同时入库的广告在翻页时重复或遗漏。仅首页走 Redis 缓存（TTL 5 分钟）。

### 搜索降级链

```
向量检索(0.2) → LLM 意图识别 + 关键词补召 → LLM 精排(0.6)
    │                  │                      │
    └─ 失败：candidates=[]     └─ 失败：原始query分词    └─ 失败：similarity≥0.3排序
```

任意阶段失败都有 fallback，搜索绝对不会返回空（除非真的没有任何匹配的广告）。

### Embedding 缓存

相同 query 的 embedding 向量缓存在 Redis 中（TTL 1 小时），避免重复调用 BGE 模型。key 基于 `hash(text)` 取低 32 位，碰撞概率极低。

### 曝光排重

基于 `X-Device-ID` + Redis Set 记录已曝光广告，Feed 接口自动过滤。

### 优雅降级

- Celery broker 不可用 → 入库成功，跳过 AI 管线触发，日志 warn
- LLM API 不可用 → 搜索降级为标签匹配，AI 管线回退到截断 `ad_text`
- Embedding 模型加载失败 → 搜索降级为关键词标签匹配，AI 管线跳过 embedding 入库

## 运行测试

```bash
# 全部测试（单元 + 集成，无需外部服务）
pytest tests/ -v

# 仅单元测试
pytest tests/ --ignore=tests/test_integration.py -v

# 仅集成测试（需 SQLite + fakeredis）
pytest tests/test_integration.py -v
```

## 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `DATABASE_URL` | `postgresql+asyncpg://aiads:aiads@localhost:5432/aiads` | 数据库连接 |
| `REDIS_URL` | `redis://localhost:6379/0` | Redis 连接 |
| `LLM_API_KEY` | — | LLM API Key（必填） |
| `LLM_MODEL` | `gpt-4o` | LLM 模型名 |
| `LLM_BASE_URL` | `https://api.openai.com/v1` | LLM API 地址 |
| `EMBEDDING_MODEL` | `BAAI/bge-large-zh-v1.5` | Embedding 模型（本地运行） |
| `EMBEDDING_DIM` | `1024` | 向量维度 |
| `EMBEDDING_DEVICE` | `cpu` | 运行设备（cpu / cuda） |
| `CELERY_BROKER_URL` | `redis://localhost:6379/1` | Celery broker |
| `FEED_CACHE_TTL` | `300` | Feed 首页缓存秒数 |
| `LLM_REQUEST_TIMEOUT` | `30` | LLM 请求超时秒数 |
