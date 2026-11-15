# Agent Platform · 开发入口

## 项目

Agent 平台管理后台，前后端分离：

- 后端：Java 21 / Spring Boot 3.5 / Spring Modulith，单 Maven 模块，包内 DDD 四层。
- 管理后台前端：`admin-web`，React 19 / TypeScript / Vite。
- 用户端：`user-web`，React 19 / TypeScript / Vite，调用 `/api/v1/user/*` 门户接口（智能体目录 + SSE 流式对话）。
- 数据：MySQL 8 + Flyway + MyBatis-Plus；测试使用 H2 MySQL 模式。
- 运行时：多模型路由（OpenAI-compatible + Anthropic）、SSE、RAG、Milvus/Elasticsearch、持久化工作流。
- 部署：Docker Compose、TKE 清单、腾讯云中间件配置。

## 项目结构

```text
agent-platform/
├── backend/
│   ├── src/main/java/com/agentplatform/
│   │   ├── AgentPlatformApplication.java      启动类，@EnableScheduling
│   │   ├── admin/                            管理端辅助接口（系统信息等）
│   │   ├── common/                           共享内核：ApiResponse、ErrorCode、DomainEvent、TypeHandler
│   │   ├── framework/                        技术框架：SecurityConfig、OpenAPI、异步线程池、
│   │   │                                     SecretCipher、JWT 黑名单、登录限流、全局异常处理
│   │   └── modules/                          Spring Modulith 限界上下文
│   │       ├── agent/                        智能体定义、模型绑定、生命周期
│   │       ├── chat/                         会话运行时、SSE、会话分析读模型
│   │       ├── model/                        厂商、模型目录、路由策略、密钥
│   │       ├── tool/                         工具目录、HTTP/内置工具、MCP 客户端
│   │       ├── knowledge/                    数据集、文档摄取、解析、Embedding、检索
│   │       ├── workflow/                     流程定义、图执行、审批、定时器、补偿
│   │       ├── release/                      发布单、灰度、回滚
│   │       ├── eval/                         评测集、评测运行、发布门禁
│   │       ├── observe/                      运营概览、会话分析、告警、成本
│   │       ├── system/                       认证、成员、API Key、审计
│   │       ├── portal/                       用户门户只读 API（/api/v1/user）
│   │       └── open/                         开放平台占位
│   ├── src/main/resources/
│   │   ├── application.yml                   数据源/Redis/Flyway/MyBatis-Plus/OTLP/工作流配置
│   │   ├── application-prod.yml              生产 profile
│   │   ├── db/migration/V1..V9__*.sql        Flyway 迁移，只增不改
│   │   ├── mapper/*.xml                      MyBatis 自定义 SQL
│   │   └── logback-spring.xml                结构化日志
│   ├── src/test/                             ApplicationContextTests、ModularityTests
│   └── pom.xml
├── admin-web/                                管理后台（React 19 + TS + Vite + Ant Design）
│   ├── src/App.tsx                           壳层、导航、主题、登录门禁
│   ├── src/api.ts                            统一 API 客户端与类型
│   ├── src/modules.ts                        导航/路由模块
│   ├── src/pages/**                          13 个管理页面
│   ├── src/components/**                     自定义 UI 组件、图标、图表
│   ├── src/styles.css                        设计 token、深浅色主题
│   ├── deploy/nginx.conf                     Nginx 静态站点与 /api 代理
│   └── Dockerfile
├── user-web/                                 用户端聊天门户（React 19 + TS + Vite）
│   ├── src/App.tsx                           智能体目录、SSE 对话、本地会话快照
│   ├── src/api.ts                            /api/v1/user/*、SSE 解析
│   ├── src/styles.css
│   └── Dockerfile
├── deploy/
│   ├── docker-compose.yml                    本地完整基础设施
│   ├── k8s/                                  TKE/K8s Deployment、Service、Ingress、HPA、Secret
│   └── server/                               单台腾讯云 CVM 部署脚本与 Compose
├── scripts/run-backend.sh                    本地打包并后台启动后端
├── docs/                                     实现状态、技术文档、演示包
├── .claude/skills/                           项目级 Claude Code skills（21 个）
├── CLAUDE.md                                 本文件：开发入口与硬性约定
├── README.md
└── Makefile                                  backend/admin-web/user-web/build/test
```

### 后端分层约定

每个 `modules/<context>/` 内部：

```text
domain/           聚合、值对象、状态机、仓储端口（不依赖 Spring/MyBatis）
application/      query（只读 DTO）+ service（用例编排、事务）
infrastructure/   persistence（Entity/Mapper/XML/Repository 适配器）、外部客户端
interfaces/       REST Controller + 请求 DTO
```

跨模块只能依赖 `application/query` 或 `application/service` 的 `@NamedInterface("api")`。

### 持久化目录约定

```text
infrastructure/persistence/
  entity/*Entity.java        @TableName(autoResultMap=true)
  mapper/*Mapper.java        BaseMapper + 自定义方法
  repository/*Repository.java 实体仓储（包装 Mapper）
  repository/Mybatis*Repository.java 领域端口适配器
resources/mapper/*.xml      自定义 SQL
```

### 前端边界

- `admin-web` 只调用管理端 `/api/v1/*`，包含所有运营、配置、调试页面。
- `user-web` 只调用 `/api/v1/user/*` 与共享的登录/会话读取接口。
- 后端字段变化必须同步对应 `api.ts` 和页面；构建会执行 `tsc --noEmit`。
- 新页面需要在 `modules.ts`、`pages/index.ts`、`App.tsx` 路由中登记。

## 常用命令

```bash
# 后端测试
cd backend
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
mvn test

# 后端构建
mvn -DskipTests package

# 启动后端（读取 .env）
cd ..
./scripts/run-backend.sh

# 前端构建
cd admin-web
npm run build

# 用户端构建
cd ../user-web
npm run build

# 本地完整基础设施
docker compose -f deploy/docker-compose.yml up -d

# 单台腾讯云 CVM 生产部署（rsync + 远端 compose 构建）
./deploy/server/deploy.sh root@<公网IP>
```

本地地址：

- 管理后台：http://localhost:5173
- 用户端：http://localhost:5174
- 后端：http://localhost:8081
- Swagger：http://localhost:8081/swagger-ui.html
- MySQL：localhost:3308（本地 `agent-mysql-local`；生产按部署配置）
- Milvus：localhost:19530
- Elasticsearch：localhost:9200

演示账号：

- `admin / admin123`（平台管理员）
- `developer / developer123`（开发者）
- `viewer / viewer123`（只读）

## 开始任何后端任务前

1. 先读对应模块和现有实现，不要凭空新建重复能力。
2. 选择相关 skill：
   - 端到端功能：`.claude/skills/backend-feature-delivery`
   - DDD 模块：`.claude/skills/backend-ddd-module`
   - 领域模型：`.claude/skills/backend-domain-model`
   - 持久化/Flyway：`.claude/skills/backend-persistence`
   - REST 接口：`.claude/skills/backend-api-endpoint`
   - 安全/权限/审计：`.claude/skills/backend-security`
   - Agent/SSE：`.claude/skills/backend-agent-runtime`
   - 多模型路由：`.claude/skills/backend-model-routing`
   - 工具执行：`.claude/skills/backend-tool-execution`
   - 知识库/RAG/网页：`.claude/skills/backend-knowledge-rag`
   - 工作流：`.claude/skills/backend-workflow-engine`
   - MCP：`.claude/skills/backend-mcp-integration`
   - 指标/告警：`.claude/skills/backend-observability`
   - 测试：`.claude/skills/backend-testing`
   - 发布/部署：`.claude/skills/backend-release-deploy`
   - 审查：`.claude/skills/backend-code-review`
   - 提交/推送/PR：`.claude/skills/git-commit`
   - 用户门户后端：`.claude/skills/backend-user-portal`
   - 管理后台前端：`.claude/skills/frontend-admin-web`
   - 用户端前端：`.claude/skills/frontend-user-web`
   - 本地启动/排障：`.claude/skills/local-development`
3. Skills 索引：`.claude/skills/README.md`。

## 不可违反的约定

- 不把运行时存储改回内存仓储；`*SeedData` 只用于首次启动初始化。
- 表结构只通过 Flyway 迁移，MyBatis-Plus Entity/Mapper 必须与迁移一致，不要手改数据库作为唯一实现。
- 新业务接口：统一 `ApiResponse`，使用 `BusinessException`/`ErrorCode`。
- 写接口必须加 `@PreAuthorize`；敏感操作只给 `PLATFORM_ADMIN`。
- 写操作由 `AuditLogFilter` 审计；不要记录密码、Token、Cookie、完整提示词。
- 跨模块只依赖 `application/query` 或 `application/service`，并加 `@NamedInterface`；domain/infrastructure 不跨模块暴露。
- 新增字段/构造器时保留兼容结构，并同步 MyBatis-Plus `@TableField`/TypeHandler 映射，避免破坏种子数据。
- 持久化目录约定：`persistence/entity` 放 `*Entity`，`persistence/mapper` 放 `*Mapper`，`persistence/repository` 放 `*Repository`；XML 放 `resources/mapper/<Entity>Mapper.xml`。Repository 包装 Mapper，多表端口适配器聚合多个 Repository。
- 前端接口契约变更时同步 `admin-web/src/api.ts`、`user-web/src/api.ts` 和对应页面。
- 不把真实密钥、`.env`、API Key、Cookie 写进代码或提交。
- 所有新增代码使用中文注释：类/接口/枚举、public 方法、关键字段、复杂状态机/降级/事务/并发/补偿逻辑。
- 任何改动至少验证：`mvn test`、`npm run build`，以及关键接口 401/403/200。
- Git 提交遵循 `.claude/skills/git-commit`：`<type>(scope): subject` + 中文正文；正文写明迁移/配置/回滚与验证结果；一个提交只做一件事。
- 提交前检查 `.env`、密钥、Token、Cookie 与生成物未入库；已推送历史不改写，禁止对共享分支 force push。

## 当前运行配置

- `.env` 本地保存 DeepSeek/Embedding/存储配置，权限 600，已被 `.gitignore` 忽略。
- 模型厂商：DeepSeek / Kimi / 千问 / Anthropic / OpenAI / 智谱；密钥在页面加密保存，DeepSeek 支持 `DEEPSEEK_API_KEY` 环境变量回退。
- 未配置 Key 时使用本地演示流；未配置 Embedding Key 时使用本地哈希向量。
- Milvus/ES 默认本地 Docker 服务；生产地址通过环境变量注入。
- 对内部 Wiki 抓取需 `ALLOW_PRIVATE_WEB=true`，并谨慎使用 Cookie。

## 文档

- [README.md](README.md)
- [实现状态](docs/implementation-status.md)
- [部署说明](deploy/README.md)
- [技能索引](.claude/skills/README.md)

## 输出要求

- 先说结论，再给关键改动和验证结果。
- 不把“演示/未验证/待生产化”说成已完成。
- 如果改动涉及数据库、安全、运行时或部署，明确列出迁移、配置和回滚方式。
