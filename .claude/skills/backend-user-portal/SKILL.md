---
name: backend-user-portal
description: 修改用户门户后端 /api/v1/user/*、可用智能体视图、状态白名单、用户端 SSE 对话入口或用户端字段裁剪时使用。
---

# 用户门户后端开发

## 先读

- `modules/portal/package-info.java`
- `modules/portal/application/query/UserAgentView.java`
- `modules/portal/interfaces/rest/UserPortalController.java`
- `modules/agent/application/service/AgentApplicationService.java`
- `modules/chat/application/runtime/AgentRuntimeService.java`
- `user-web/src/api.ts`、`user-web/src/App.tsx`

## 模块定位

- portal 不拥有领域数据。
- 只复用 agent 查询和 chat 运行时。
- 不暴露管理端指标、提示词全文、知识库编码、工具密钥。
- 只允许 `running` / `grayscale` 智能体对用户开放。

## 接口

```text
GET  /api/v1/user/agents
POST /api/v1/user/chat:stream
GET  /api/v1/sessions/{id}/messages
```

- `/user/agents`：过滤可用状态，映射 `UserAgentView`。
- `/user/chat:stream`：校验消息非空、智能体存在、状态可用；返回 `SseEmitter`。
- `/sessions/{id}/messages`：由 chat 模块提供，用户端读取历史。

## UserAgentView

只暴露：

```text
id, name, code, team, description, model, status, tools, knowledgeBases(数量)
```

- `description` 从 prompt 首行裁剪，去掉“你是”和结尾标点，最长 80 字。
- 不返回 owner、cost、sessions、p95、prompt 全文、kb 编码。
- tools 目前是编码列表；如果未来包含敏感信息要再裁剪。

## 安全

- 用户端接口只要求登录，不加 `@PreAuthorize`。
- 状态白名单在 Controller 内强制；不能只靠前端过滤。
- 草稿/暂停智能体统一返回“暂未开放”，避免暴露内部状态。
- 用户只能访问自己可用的智能体？当前没有租户/数据权限，只有全局登录；这是待补缺口。
- SSE 走 `AgentRuntimeService`，仍使用服务端模型/工具/知识库权限。

## SSE

- `produces = MediaType.TEXT_EVENT_STREAM_VALUE`。
- 事件契约与 `backend-agent-runtime` 一致。
- 用户端 SSE 解析器必须容忍 `meta/delta/tool/tool-result/done/error`。
- 错误时返回 JSON 错误包（在建立 SSE 前校验）。

## 新增用户端能力

1. 先定义用户可见字段，禁止直接返回管理端 DTO。
2. 在 portal 加 query/controller，或复用已有 NamedInterface。
3. 状态/权限白名单。
4. 更新 `user-web/src/api.ts`。
5. 处理空态/错误。
6. 验证 401、非可用智能体、SSE。
7. 中文注释。

## 当前限制

- 没有用户-智能体授权表；只有全局状态白名单。
- 没有多租户隔离。
- 用户端没有工具/知识库细粒度展示。
- 用户端没有会话列表分页/删除。
- 用户门户没有独立登录页品牌/SSO 配置。

## Checklist

- [ ] 只返回用户可见字段。
- [ ] 状态白名单。
- [ ] SSE 契约一致。
- [ ] 管理端字段不泄漏。
- [ ] 401/404/SSE 验证。
- [ ] 中文注释。
