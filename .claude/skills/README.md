# 后端开发 Skills

这些 skills 是项目级 Claude Code 技能，位于 `.claude/skills/`。当任务匹配 `description` 时会自动加载，也可以直接引用对应技能名。

## 技能目录

| Skill | 何时使用 |
|---|---|
| [backend-feature-delivery](backend-feature-delivery/SKILL.md) | 端到端交付一个后端功能 |
| [backend-ddd-module](backend-ddd-module/SKILL.md) | 新增/重构 DDD 模块和四层结构 |
| [backend-domain-model](backend-domain-model/SKILL.md) | 聚合、状态机、值对象、仓储端口 |
| [backend-persistence](backend-persistence/SKILL.md) | Flyway、JPA Entity、仓储适配器 |
| [backend-api-endpoint](backend-api-endpoint/SKILL.md) | 新增/修改 REST 接口、DTO、权限、OpenAPI |
| [backend-security](backend-security/SKILL.md) | JWT/OIDC、RBAC、审计、Redis 限流 |
| [backend-agent-runtime](backend-agent-runtime/SKILL.md) | DeepSeek SSE、提示词、RAG/工具上下文、会话消息 |
| [backend-knowledge-rag](backend-knowledge-rag/SKILL.md) | 文件/网页摄取、解析、Embedding、Milvus/ES |
| [backend-workflow-engine](backend-workflow-engine/SKILL.md) | 流程实例、节点执行、审批恢复 |
| [backend-mcp-integration](backend-mcp-integration/SKILL.md) | MCP Client、工具发现、tools/call |
| [backend-observability](backend-observability/SKILL.md) | Micrometer、OTLP、告警、通知 |
| [backend-testing](backend-testing/SKILL.md) | 单元、上下文、Modulith、接口测试 |
| [backend-release-deploy](backend-release-deploy/SKILL.md) | 构建、Docker、TKE、腾讯云 |
| [backend-code-review](backend-code-review/SKILL.md) | 后端代码审查清单 |

## 常用组合

- 新增一个业务能力：`backend-feature-delivery` → `backend-persistence` → `backend-api-endpoint` → `backend-security` → `backend-testing`。
- 接入 AI 能力：`backend-agent-runtime` → `backend-knowledge-rag` → `backend-mcp-integration`。
- 上线前：`backend-testing` → `backend-code-review` → `backend-release-deploy`。

## 项目基线

- Java 21 / Spring Boot 3.5 / Spring Modulith。
- 单 Maven 模块，包内 DDD 分层。
- MySQL + Flyway + JPA；测试用 H2 MySQL 模式。
- JWT/OIDC + RBAC + Redis 限流/黑名单 + 审计。
- DeepSeek SSE + RAG + Milvus/ES + 工作流执行器。
- 前端 React 19 + TypeScript + Vite。

## 命令

```bash
cd backend && mvn test
cd frontend && npm run build
```

Skills 不是替代测试：任何改动仍以真实构建、接口和页面验证为准。
