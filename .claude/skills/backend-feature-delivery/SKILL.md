---
name: backend-feature-delivery
description: 端到端完成后端功能时使用；从需求定位模块，串联 DDD、持久化、接口、安全、测试和前端契约，避免只改一层。
---

# 后端功能交付流程

## 1. 明确范围

先确定：

- 属于哪个限界上下文（agent/knowledge/model/chat/workflow/tool/release/eval/observe/system）。
- 是新增聚合、扩展字段、增加接口还是纯查询。
- 是否涉及数据库迁移、权限、审计、外部服务、前端页面。

不确定时先读对应模块和 `docs/implementation-status.md`。

## 2. 选择技能

| 工作 | 技能 |
|---|---|
| 新模块/分层 | `backend-ddd-module` |
| 领域规则 | `backend-domain-model` |
| 表/字段/仓储 | `backend-persistence` |
| REST 接口 | `backend-api-endpoint` |
| 权限/审计 | `backend-security` |
| Agent/SSE | `backend-agent-runtime` |
| 知识库/RAG | `backend-knowledge-rag` |
| 工作流 | `backend-workflow-engine` |
| MCP | `backend-mcp-integration` |
| 指标/告警 | `backend-observability` |
| 测试 | `backend-testing` |
| 部署 | `backend-release-deploy` |
| 审查 | `backend-code-review` |

## 3. 实施顺序

1. 迁移：新增 `V{n}__*.sql`。
2. domain：聚合、状态流转、仓储端口。
3. infrastructure：Entity、JPA、适配器、事务。
4. application：业务编排、权限、审计。
5. interfaces：DTO、Controller、OpenAPI。
6. 前端契约：`api.ts` + 页面。
7. 测试：单元 + 上下文 + 接口 + Modulith。
8. 构建：`mvn test`、`npm run build`。
9. 文档：更新 `README.md` / `docs/implementation-status.md`。

## 4. 完成定义

- [ ] 数据落库，重启不丢失。
- [ ] 接口有认证/授权和统一错误码。
- [ ] 写操作有审计。
- [ ] 前端可操作（如涉及）。
- [ ] 关键正常/异常路径有测试。
- [ ] `mvn test` 与 `npm run build` 通过。
- [ ] 没有密钥、`.env` 或本地文件被提交。
- [ ] 未完成项明确写入实现状态，不宣称已生产化。
