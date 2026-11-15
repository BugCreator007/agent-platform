# Agent Platform 开发 Skills

这些是项目级 Claude Code 技能，位于 `.claude/skills/`。当任务匹配 `description` 时会自动加载，也可以直接引用技能名。

## 体系

- **基础交付**：功能端到端、DDD 模块、领域模型、持久化、接口、安全。
- **运行时能力**：Agent 运行时、多模型路由、工具执行、MCP、知识库 RAG、工作流。
- **质量与上线**：可观测性、测试、代码审查、发布部署。
- **前端与本地**：管理后台、用户端、用户门户后端、本地开发。

## 技能目录

| Skill | 何时使用 |
|---|---|
| [backend-feature-delivery](backend-feature-delivery/SKILL.md) | 端到端交付一个功能，串联后端/数据库/两个前端 |
| [backend-ddd-module](backend-ddd-module/SKILL.md) | 新增/重构 Spring Modulith 模块和四层结构 |
| [backend-domain-model](backend-domain-model/SKILL.md) | 聚合、状态机、值对象、领域仓储端口 |
| [backend-persistence](backend-persistence/SKILL.md) | Flyway、MyBatis-Plus Entity/Mapper/XML/Repository、TypeHandler |
| [backend-api-endpoint](backend-api-endpoint/SKILL.md) | REST/SSE 接口、DTO、ApiResponse、权限、OpenAPI |
| [backend-security](backend-security/SKILL.md) | JWT/OIDC、RBAC、审计、Redis 限流/黑名单、密钥加密 |
| [backend-agent-runtime](backend-agent-runtime/SKILL.md) | SSE 对话、提示词、RAG 上下文、MCP 工具循环、会话持久化 |
| [backend-model-routing](backend-model-routing/SKILL.md) | Provider/Model、OpenAI-compatible/Anthropic、主备路由、密钥与探测 |
| [backend-tool-execution](backend-tool-execution/SKILL.md) | 工具目录、内置/HTTP 工具、weather.query、健康检查 |
| [backend-mcp-integration](backend-mcp-integration/SKILL.md) | MCP 三传输、initialize/tools/list/tools/call、工具目录同步 |
| [backend-knowledge-rag](backend-knowledge-rag/SKILL.md) | 文件/网页摄取、解析、切分、Embedding、Milvus/ES、混合检索 |
| [backend-workflow-engine](backend-workflow-engine/SKILL.md) | 图执行、并行/汇聚、条件、子流程、补偿、定时器、NOTIFY、审批 |
| [backend-observability](backend-observability/SKILL.md) | 运营概览、会话分析、成本、Micrometer、Prometheus、告警、企微 |
| [backend-testing](backend-testing/SKILL.md) | 单元、上下文、MyBatis、Modulith、接口、SSE、工作流 E2E |
| [backend-release-deploy](backend-release-deploy/SKILL.md) | Docker、Compose、腾讯云 CVM、TKE、健康检查、备份回滚 |
| [backend-code-review](backend-code-review/SKILL.md) | 后端/全栈代码审查清单与严重级别 |
| [backend-user-portal](backend-user-portal/SKILL.md) | `/api/v1/user/*`、用户可见智能体、状态白名单、用户 SSE 入口 |
| [frontend-admin-web](frontend-admin-web/SKILL.md) | 管理后台 React 页面、API 契约、主题、交互 |
| [frontend-user-web](frontend-user-web/SKILL.md) | 用户端 React、SSE 解析、会话持久化、停止生成、移动端 |
| [local-development](local-development/SKILL.md) | 本地启动 MySQL/Redis/ES/Milvus、后端和两个前端，排查端口/迁移问题 |

## 常用组合

- 新业务功能：`backend-feature-delivery` → `backend-ddd-module` → `backend-domain-model` → `backend-persistence` → `backend-api-endpoint` → `backend-security` → `backend-testing`。
- Agent 运行时：`backend-agent-runtime` → `backend-model-routing` → `backend-tool-execution` → `backend-mcp-integration` → `backend-knowledge-rag`。
- 工作流：`backend-workflow-engine` → `backend-tool-execution` → `backend-observability`。
- 前端：`frontend-admin-web` / `frontend-user-web` + `backend-api-endpoint` / `backend-user-portal`。
- 上线：`backend-testing` → `backend-code-review` → `backend-release-deploy`。
- 本地排障：`local-development`。

## 项目基线

- Java 21 / Spring Boot 3.5 / Spring Modulith / MyBatis-Plus。
- MySQL 8 + Flyway；测试 H2 MySQL 模式。
- JWT/OIDC + RBAC + Redis 限流/黑名单 + 审计。
- 多模型路由（OpenAI-compatible + Anthropic）、SSE、RAG、Milvus/ES、MCP、持久化工作流。
- 管理端 `admin-web`（5173）、用户端 `user-web`（5174）。

## 命令

```bash
cd backend && export JAVA_HOME=$(/usr/libexec/java_home -v 21) && mvn test
cd admin-web && npm run build
cd user-web && npm run build
```

Skills 不是替代测试：任何改动仍以真实构建、接口、页面和迁移验证为准。

## 注释规范

所有新增代码必须使用中文注释：

- 类/接口/枚举说明所属层、职责、关键约束。
- public 方法说明用途、参数、返回值和异常。
- 关键字段说明 ID、状态、JSON、时间、密钥、外部地址。
- 复杂逻辑说明状态机、降级、事务、并发、幂等、补偿。
- 注释解释“为什么/约束”，不要只复述代码。
