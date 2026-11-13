# Agent Platform · 开发入口

## 项目

Agent 平台管理后台，前后端分离：

- 后端：Java 21 / Spring Boot 3.5 / Spring Modulith，单 Maven 模块，包内 DDD 四层。
- 前端：React 19 / TypeScript / Vite。
- 数据：MySQL 8 + Flyway + JPA；测试使用 H2 MySQL 模式。
- 运行时：多模型路由（OpenAI-compatible + Anthropic）、SSE、RAG、Milvus/Elasticsearch、持久化工作流。
- 部署：Docker Compose、TKE 清单、腾讯云中间件配置。

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
cd frontend
npm run build

# 本地完整基础设施
docker compose -f deploy/docker-compose.yml up -d

# 单台腾讯云 CVM 生产部署（rsync + 远端 compose 构建）
./deploy/server/deploy.sh root@<公网IP>
```

本地地址：

- 前端：http://localhost:5173
- 后端：http://localhost:8081
- Swagger：http://localhost:8081/swagger-ui.html
- MySQL：localhost:3307
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
   - 知识库/RAG/网页：`.claude/skills/backend-knowledge-rag`
   - 工作流：`.claude/skills/backend-workflow-engine`
   - MCP：`.claude/skills/backend-mcp-integration`
   - 指标/告警：`.claude/skills/backend-observability`
   - 测试：`.claude/skills/backend-testing`
   - 发布/部署：`.claude/skills/backend-release-deploy`
   - 审查：`.claude/skills/backend-code-review`
3. Skills 索引：`.claude/skills/README.md`。

## 不可违反的约定

- 不把运行时存储改回内存仓储；`*SeedData` 只用于首次启动初始化。
- 表结构只通过 Flyway 迁移，`ddl-auto=validate`，不要手改数据库作为唯一实现。
- 新业务接口：统一 `ApiResponse`，使用 `BusinessException`/`ErrorCode`。
- 写接口必须加 `@PreAuthorize`；敏感操作只给 `PLATFORM_ADMIN`。
- 写操作由 `AuditLogFilter` 审计；不要记录密码、Token、Cookie、完整提示词。
- 跨模块只依赖 `application/query` 或 `application/service`，并加 `@NamedInterface`；domain/infrastructure 不跨模块暴露。
- 新增字段/构造器时保留兼容重载，避免破坏 JPA 映射和种子数据。
- 前端接口契约变更时同步 `frontend/src/api.ts` 和对应页面。
- 不把真实密钥、`.env`、API Key、Cookie 写进代码或提交。
- 任何改动至少验证：`mvn test`、`npm run build`，以及关键接口 401/403/200。

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
