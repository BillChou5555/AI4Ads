# AI4Ads — AI 智能广告信息流 App

一款主打"极速体验"与"AI 内容理解"的单列广告信息流 Android 应用。

核心价值：利用 AI 摘要与标签提升广告浏览效率，通过本地标签过滤与自然语言检索降低用户筛选门槛。

[**演示视频**](演示视频.mp4)

## 完整功能清单

| 模块 | 功能 | 客户端 | 服务端 |
|------|------|:------:|:------:|
| M1 | 项目基础架构（网络层 / 数据层 / DI / 导航骨架） | ✅ | ✅ |
| M2 | 顶部 Tab 导航（精选 / 电商 / 本地，点击 + 滑动切换） | ✅ | — |
| M3 | 信息流列表渲染（4 种卡片类型自动判定、cursor 分页） | ✅ | ✅ |
| M4 | 视频播放器（PlayerPool 池化、PlaybackManager 自动调度） | ✅ | — |
| M5 | 性能优化（Glide 缓存策略、ExoPlayer 视频缓存、预加载） | ✅ | — |
| M6 | AI 摘要与标签展示（Chip 动态生成、标签选中态同步） | ✅ | ✅ |
| M7 | 标签本地过滤（AND 逻辑过滤、FilterBar 提示条） | ✅ | — |
| M8 | 广告详情页（ViewPager2 媒体翻页、视频控制、右滑返回） | ✅ | — |
| M9 | 用户交互（点赞 / 收藏 / 转发，SQLite 持久化，缩放动画） | ✅ | — |
| M10 | 数据埋点看板（本地事件采集 + MPAndroidChart 柱状图） | ✅ | — |
| M11 | 物料入库与 AI 预处理（LLM 摘要 / 标签、去重、向量化） | — | ✅ |
| M12 | 信息流分发与自然语言搜索（RAG：向量 → LLM 精排） | — | ✅ |
| M13 | 自然语言搜索页（历史记录、结果复用 Feed 卡片） | ✅ | — |

## 技术栈

### 客户端

| 类别 | 选型 | 说明 |
|------|------|------|
| 语言 | Kotlin | — |
| 最低 SDK | API 24 (Android 7.0) | — |
| UI | XML View + ViewBinding | RecyclerView 复用机制成熟 |
| 架构 | MVVM + 手动 DI (AppContainer) | `by lazy` 懒加载，依赖链显式可见 |
| 网络 | Retrofit 2 + OkHttp 4 + Gson | Retrofit 定义接口，OkHttp 执行请求 |
| 图片 | Glide 4.16 | 内存 + 磁盘两级缓存，`Priority.IMMEDIATE` 首屏优先 |
| 视频 | ExoPlayer (Media3) | PlayerPool 池化复用（最多 3 实例） |
| 本地存储 | SQLiteOpenHelper | 3 张表：user_interactions / search_history / analytics_events |
| 图表 | MPAndroidChart v3.1 | 数据看板柱状图 |
| 导航 | Navigation Component | 单 Activity，Fragment 间跳转 |
| 响应式 | Kotlin Coroutines + Flow + LiveData | Flow 用于数据流，LiveData 用于 UI 观察 |

### 服务端

| 类别 | 选型 | 说明 |
|------|------|------|
| 语言 | Python 3.11+ | — |
| Web 框架 | FastAPI (uvicorn) | 异步路由 + 自动 OpenAPI 文档 |
| 数据库 | PostgreSQL 16 + pgvector | 广告存储 + 向量检索 |
| 缓存 | Redis 7 | Feed 首页缓存 + Embedding 缓存 + Celery broker |
| 异步任务 | Celery | AI 摘要 / 标签 / Embedding 生成 + 缓存预热 |
| LLM | OpenAI 兼容 API | DeepSeek / GPT-4o 等，通过 `LLM_BASE_URL` 配置 |
| Embedding | BAAI/bge-large-zh-v1.5 | 本地 CPU 运行，1024 维向量，Redis 缓存 |
| 部署 | Docker Compose | API + Worker + Beat + PostgreSQL + Redis + Nginx |

## 项目结构

```
AI4Ads/
├── android/app/                             # Android 客户端
│            └── src/main/java/com/aiads/
│                 ├── analytics/               # 埋点采集（AnalyticsManager）
│                 ├── data/
│                 │   ├── local/               # SQLite（DatabaseHelper）
│                 │   ├── model/               # 数据模型（Ad, AnalyticsEvent, …）
│                 │   ├── remote/              # Retrofit API（AdApi, SearchApi）
│                 │   └── repository/          # 数据仓库（FeedRepository, InteractionRepository）
│                 ├── di/                      # 手动 DI（AppContainer）
│                 ├── player/                  # 播放器池（PlayerPool, PlaybackManager）
│                 ├── ui/
│                 │   ├── analytics/           # 数据看板（DashboardFragment + BarChart）
│                 │   ├── detail/              # 详情页（SwipeBackLayout + ViewPager2）
│                 │   ├── feed/                # 信息流（FeedFragment / FeedTabFragment / FilterBar）
│                 │   │   └── cards/           # 4 种卡片 Adapter
│                 │   └── search/              # 搜索页（SearchFragment + SearchViewModel）
│                 └── util/                    # 工具类（DeviceIdProvider）
├── server/                          # FastAPI 服务端
│   └── app/
│       ├── api/v1/                  # 路由（ads, search）
│       ├── services/                # 业务逻辑（AdService, SearchService, LLMClient, …）
│       ├── tasks/                   # Celery 任务（AI 管线 + 缓存预热）
│       ├── models/                  # ORM 模型
│       ├── schemas/                 # Pydantic Schema
│       ├── core/                    # 基础设施（database, redis, celery）
│       └── middleware/              # 日志 / 限流
└── docs/                            # 文档
    ├── 项目需求文档.md
    ├── 技术方案文档.md
    └── 代码开发规范.md
 
```

## 快速开始

### 服务端

```bash
cd server
cp .env.example .env
# 编辑 .env，填入 LLM_API_KEY
docker compose up -d
docker compose exec api alembic upgrade head
docker compose exec api python scripts/import_ads.py scripts/ads.json --clear
```

验证：
```bash
curl http://localhost:8000/health       # {"status":"ok"}
curl http://localhost:8000/api/v1/ads/feed?tab=featured
```

### 客户端

用 Android Studio 打开项目根目录，Sync Gradle，运行 `app` 模块。

- 模拟器：服务端地址自动使用 `http://10.0.2.2:8000/`
- 真机：修改 `app/build.gradle.kts` 中 `HOST_URL` 为电脑局域网 IP，并确认 `AndroidManifest.xml` 已配置 `networkSecurityConfig`（允许内网 HTTP 明文）

## 架构核心设计

### 搜索策略（RAG 四阶段流水线）

```
用户输入 → BGE Embedding(1024d) → pgvector Cosine≥0.2 粗筛 Top-50
  → LLM 意图识别 + 关键词补召 →
  → LLM 逐条独立打分(0.6 阈值过滤) → 分页返回
```

任意阶段失败自动降级，搜索绝不返回空。详见 `server/README.md` 第三章。

### 视频播放调度

`PlaybackManager` 监听 RecyclerView 滚动，以屏幕中心 ±25% 为核心播放区。进入区域的视频自动静音播放，离开区域自动暂停，播放器通过 `PlayerPool`（最多 3 实例）池化复用。

### 数据流

```
服务端: 物料上传 → LLM 摘要+标签 → BGE 向量化 → pgvector 存储
                                                      ↓
客户端: Feed 列表 ← cursor 分页 + Redis 缓存  ←  API
           ├── 标签点击 → 本地 AND 过滤
           ├── 点赞/收藏/转发 → SQLite 持久化
           ├── 卡片点击 → 详情页（Video/Image ViewPager2）
           └── 搜索 → RAG 流水线 → 结果复用 Feed 卡片
```

---

## AI 辅助开发声明

本项目在开发过程中使用了 AI 编程助手（Claude Code）。以下是 AI 使用的具体方式、验证策略与优化实践。

### 1. AI 参与方式

| 阶段 | AI 角色 | 人类角色 |
|------|---------|----------|
| 需求分析 | 阅读需求文档，拆分为可执行模块 | 审核模块划分是否合理，确认技术决策 |
| 技术方案 | 逐一提出每个模块的技术选型与方案对比 | 根据项目约束选择方案，补充特殊要求 |
| 编码实现 | 编写全部代码，边写边讲解设计意图 | 充分理解代码实现，编译验收，调试运行，提出修改意见 |
| 文档编写 | 整理技术方案文档、代码开发规范 | 审核，补充遗漏 |
| 代码维护 | 辅助理解代码功能 | 掌握代码结构，提出优化方案 |

### 2. 模块化拆解

开发前将需求拆解为 13 个模块（M1-M13），每个模块有明确的输入、输出和依赖关系。按依赖顺序实现：

```
M1(基础) → M2(Tab) → M3(列表)
                ├── M4(视频) → M8(详情) → M9(交互) → M10(埋点看板)
                ├── M6(摘要标签) → M7(过滤)
                ├── M13(搜索)
                └── M5(性能优化)
M11(入库) + M12(分发搜索) ← 服务端
```

### 3. 验证方式

| 验证层级 | 方法 | 发现问题示例 |
|----------|------|-------------|
| 编译验证 | `./gradlew assembleDebug` | 嵌套 setOnClickListener 导致按钮需点两次 |
| 运行验证 | Android Studio 真机运行 | SwipeRefreshLayout GONE 遮挡搜索结果列表 |
| 逻辑审查 | 逐行阅读代码，确认与方案一致 | 多图卡片 Glide 循环未添加缓存策略 |
| 边界检查 | 对照需求文档逐条核对 | 搜索无结果时 Loading 动画结束后页面空白 |

### 4. 技术适配

- **依赖注入**：手动 DI (AppContainer)，更显式、无注解处理器依赖
- **数据库**：原生 SQLiteOpenHelper，免 KSP/KAPT 兼容性问题
- **埋点**：本地 SQLite + MPAndroidChart 看板，纯本地实现
- **搜索**：搜索结果复用 Feed 的 ConcatAdapter（4 种卡片 + 视频播放），零重复代码

### 5. 使用原则

- AI 是**编码执行者**，不是**决策者**。每个模块的技术方案由人确认后 AI 才开始编码。
- AI 生成的每一行代码都需要**通过编译 + 通过运行**才算完成。
- 遇到兼容性问题时，AI 会提出替代方案，由人做最终选择。
- 文档与代码同步更新，代码与文档不一致时，由 AI 向人类提出方案变更申请。
