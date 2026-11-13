# Agent Platform

Agent 平台管理后台，前后端分离，后端按 DDD 限界上下文组织，支持 MySQL 持久化、JWT/OIDC、RBAC、审计、多模型流式对话（DeepSeek / Kimi / 千问 / Anthropic 等）、知识库摄取与混合检索、工作流执行和腾讯云部署。

## 目录

```text
agent-platform
├── backend        # Spring Boot 3.5 / Java 21，单 Maven 模块，内部 DDD 分包
├── frontend       # React 19 + TypeScript + Vite
├── deploy         # Docker Compose、TKE/K8s、CI
├── docs           # 工程说明与实现状态
└── README.md
```

## 本地启动

### 方式一：Docker Compose（推荐）

```bash
cd /Users/lu/面试/agent-platform
export DEEPSEEK_API_KEY=sk-...        # 可选；不设置时使用本地演示模型
docker compose -f deploy/docker-compose.yml up -d
```

启动后：

- 前端：http://localhost:5173
- 后端：http://localhost:8081
- Swagger：http://localhost:8081/swagger-ui.html
- 演示账号：`admin / admin123`、`developer / developer123`、`viewer / viewer123`

### 方式二：本地 MySQL + 本地开发服务

当前工程已有 MySQL、Redis 时：

```bash
# 创建数据库和账号（示例）
mysql -uroot -p -e "CREATE DATABASE agent_platform CHARACTER SET utf8mb4; CREATE USER 'agent'@'%' IDENTIFIED BY 'agent123'; GRANT ALL ON agent_platform.* TO 'agent'@'%';"

cd backend
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
mvn spring-boot:run

cd ../frontend
npm install
npm run dev
```

默认连接 `jdbc:mysql://localhost:3307/agent_platform`（可用 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 覆盖）。Flyway 会自动执行 `db/migration` 下的迁移。

## 关键配置

| 能力 | 环境变量 |
|---|---|
| MySQL | `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`DB_POOL_SIZE` |
| Redis | `REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD` |
| JWT | `JWT_SECRET`（至少 32 字节）、`JWT_EXPIRES_MINUTES` |
| OIDC | `OIDC_ISSUER_URI`；设置后同时接受 OIDC 与本地 JWT |
| 模型厂商 | 页面加密保存 DeepSeek / Kimi / 千问 / Anthropic / OpenAI / 智谱 API Key；DeepSeek 可使用 `DEEPSEEK_API_KEY`、`DEEPSEEK_BASE_URL`、`DEEPSEEK_MODEL` 作为环境变量回退 |
| Embedding | `EMBEDDING_BASE_URL`、`EMBEDDING_API_KEY`、`EMBEDDING_MODEL`、`EMBEDDING_DIMENSION` |
| Milvus | `MILVUS_ENABLED`、`MILVUS_URL`、`MILVUS_COLLECTION` |
| Elasticsearch | `ES_ENABLED`、`ES_URL`、`ES_INDEX` |
| 文件存储 | `STORAGE_TYPE=local|cos`、`STORAGE_LOCAL_ROOT`、`COS_*` |
| MCP | `MCP_ALLOW_PRIVATE_URLS`、`MCP_STDIO_ALLOWED_COMMANDS` |
| 企微告警 | `app.notify.wecom-webhook` |
| 引导管理员 | `BOOTSTRAP_ADMIN_PASSWORD` |

Agent 运行时按智能体绑定的 `providerId + modelId` 选择真实模型：OpenAI-compatible 协议覆盖 DeepSeek、Kimi、千问、OpenAI、智谱，Anthropic 使用 Messages API。主模型失败且尚未输出内容时按路由策略切换备用模型；完全没有可用 Key 时才使用本地演示流。Embedding 未配置时使用确定性本地哈希向量，配置后使用 OpenAI-compatible `/embeddings`。

## 已实现能力

### 数据与安全

- MySQL 8 + Flyway 迁移 + JPA 实体/仓储，所有业务数据落库。
- JWT 登录、OIDC Resource Server 兼容、RBAC（PLATFORM_ADMIN / DEVELOPER / EVALUATOR / VIEWER）。
- Redis 登录失败限流、JWT 登出黑名单。
- 管理员可删除智能体、模型厂商和调试会话（审计记录保留）。
- 审计日志：登录、退出、所有写操作自动记录。

### Agent 运行时

- 多厂商流式适配：DeepSeek / Kimi / 千问 / OpenAI / 智谱（OpenAI-compatible）与 Anthropic Messages（SSE）。
- 智能体级模型绑定、真实 Provider 连通性测试、AES-GCM 密钥存储与备用模型故障转移。
- 智能体提示词、工具、知识库上下文组装。
- 知识库混合检索结果注入系统提示词。
- 简单工具调用上下文（订单/退款场景）。
- 会话与调用链持久化；前端沙盒调试通过 SSE 实时显示。

### 知识库

- 文件上传（multipart）到本地或腾讯云 COS。
- PDF、DOCX、Markdown、TXT、HTML 解析。
- 按标题/段落/长度切分，支持重叠。
- Embedding 客户端（OpenAI-compatible，未配置时哈希向量）。
- Milvus 向量库 + Elasticsearch 关键词索引，混合检索与 RRF 融合。
- 文档替换、版本、回滚、异步索引、重建索引、检索测试。

### 工作流

- 流程实例持久化（`workflow_instances`、`workflow_node_runs`）。
- 顺序节点执行：START、KNOWLEDGE、MODEL、TOOL、CONDITION、APPROVAL、NOTIFY、END。
- 节点重试、失败记录、执行日志。
- 人工审批等待与恢复。
- 可选定时触发（`WORKFLOW_SCHEDULER_ENABLED=true`）。

### 运维与发布

- Prometheus 指标、OpenTelemetry OTLP tracing 配置。
- 告警规则持久化、定时评估、企微 Webhook 通知。
- 发布审批接入评测门禁（`app.release.min-eval-score`）。
- Dockerfile、Docker Compose、TKE Deployment/Service/Ingress/HPA、CI 示例。

## 验证

```bash
cd backend
mvn test

cd ../frontend
npm run build
```

`mvn test` 包含 Spring Modulith 模块边界校验和 H2 上下文加载测试。

## 目录说明

- `docs/implementation-status.md`：实现状态与剩余边界。
- `deploy/README.md`：腾讯云 TKE/中间件部署说明。
- `deploy/docker-compose.yml`：本地 MySQL、Redis、Milvus、Elasticsearch 与前后端。
