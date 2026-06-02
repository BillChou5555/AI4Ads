# AI4Ads — AI 智能广告信息流 App

一款主打"极速体验"与"AI 内容理解"的单列广告信息流 Android 应用。

## 技术栈

| 类别 | 选型 |
|------|------|
| 语言 | Kotlin |
| UI | XML View + ViewBinding |
| 架构 | MVVM + 手动 DI (AppContainer) |
| 网络 | Retrofit 2 + OkHttp + Gson |
| 图片 | Glide |
| 视频 | ExoPlayer (Media3) |
| 本地存储 | SQLiteOpenHelper |
| 导航 | Navigation Component（单 Activity） |
| 服务端 | Python FastAPI + PostgreSQL + pgvector + Redis + Celery |

## 项目结构

```
AI4Ads/
├── android/                  # Android 客户端
├── server/                   # 服务端（FastAPI）
└── docs/                     # 文档
    ├── 项目需求文档.md
    ├── 技术方案文档.md
    └── 代码开发规范.md
```

## 快速开始

### 服务端

```bash
cd server
cp .env.example .env
# 编辑 .env 填入 LLM_API_KEY
docker compose up -d
docker compose exec api alembic upgrade head
docker compose exec api python scripts/import_ads.py scripts/ads.json --clear
docker compose exec api python scripts/backfill_embeddings.py
```

### 客户端

用 Android Studio 打开项目根目录，Sync Gradle，运行 `app` 模块。

---

## AI 辅助开发声明

本项目在开发过程中使用了 AI 编程助手（Claude Code）。以下是 AI 使用的具体方式、验证策略与优化实践。

### 1. AI 参与方式

| 阶段 | AI 角色 | 人类角色 |
|------|---------|----------|
| 需求分析 | 阅读需求文档，拆分为可执行模块 | 审核模块划分是否合理，确认技术决策 |
| 技术方案 | 逐一提出每个模块的技术选型与方案对比 | 根据项目约束选择方案，补充特殊要求 |
| 编码实现 | 编写全部代码，边写边讲解设计意图 | 提供编码思路，审查理解代码，检查实现与技术文档一致性，验收编译结果，调试运行，提出修改意见 |
| 文档编写 | 整理技术方案文档、代码开发规范 | 审核，补充遗漏 |
| 代码维护 | 辅助理解代码功能 | 充分掌握代码实现及代码结构，提出优化方案 |

### 2. 对功能的理解

在编码开始前，项目需求已完成以下结构化拆解：

- **13 个模块**覆盖全部需求（M1-M13），每个模块有明确的输入、输出和依赖关系。
- **数据流链路**已梳理：服务端物料入库 → AI 预处理 → Feed 分发 → 客户端渲染 → 本地过滤 → 埋点上报 → 搜索。
- **边界条件**已在方案讨论中明确（如"切换 Tab 不清除过滤"、"下拉刷新不清除过滤"等交互边界）。

### 3. AI 输出验证方式

AI 生成的代码通过以下方式验证：

| 验证层级 | 方法 | 发现问题示例 |
|----------|------|-------------|
| 编译验证 | `./gradlew assembleDebug` | AGP 9.x 与 Hilt/KSP 版本兼容性 → 切换为手动 DI |
| 运行验证 | Android Studio 真机/模拟器运行 | Activity 包名缓存问题 → 清理构建缓存 |
| 逻辑审查 | 逐行阅读代码，确认与方案一致 | Room @Entity 注解误残留 → 同步清理 |
| 边界检查 | 对照需求文档逐条核对 | 过滤、搜索、埋点等逻辑边界确认 |

### 4. 技术适配与优化

开发过程中根据实际环境做出的关键优化：

- **依赖注入简化**：原方案使用 Hilt (Dagger)，因环境兼容性问题切换为手动 DI（AppContainer 模式）。手动 DI 依赖关系更显式，降低了初学者的理解门槛。
- **数据库简化**：原方案使用 Room ORM，切换为 Android 原生 SQLiteOpenHelper，免去注解处理器依赖，代码更直接可控。
- **结构化输出约束**：技术方案文档采用统一的模块模板（方案 / 关键技术选型 / 确认点），确保每个模块讨论结构一致、不遗漏关键决策。

### 5. 使用原则

- AI 是**编码执行者**，不是**决策者**。每个模块的技术方案由人确认后 AI 才开始编码。
- AI 生成的每一行代码都需要**通过编译 + 通过运行**才算完成。
- 遇到兼容性问题时，AI 会提出替代方案，由人做最终选择。
- 文档与代码同步更新，代码与文档不一致时，需要由AI向人类提出方案变更申请，经讨论无误后变更。
