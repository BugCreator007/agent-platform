---
name: backend-agent-runtime
description: 修改 Agent 运行时、DeepSeek 流式对话、SSE、提示词组装、RAG/工具上下文或会话消息持久化时使用。
---

# Agent 运行时开发

## 先读

- `modules/chat/application/runtime/AgentRuntimeService.java`
- `modules/chat/infrastructure/ai/DeepSeekClient.java`
- `modules/chat/interfaces/rest/RuntimeController.java`
- `modules/chat/domain/model/{ChatSession,SessionMessage}.java`
- `modules/knowledge/application/service/KnowledgeApplicationService.java`
- `modules/tool/application/service/ToolExecutionService.java`
- 前端 `pages/Debug.tsx`、`pages/Agents.tsx` 的 DebugDrawer

## 运行时链路

```text
POST /api/v1/runtime/sessions:stream
 → 加载 Agent
 → 组装提示词 + 知识上下文 + 工具上下文
 → 按 providerId/modelId 选择模型
 → DeepSeek SSE
 → 发送 meta/delta/done/error
 → 持久化 SessionMessage 与 ChatSession
```

## SSE 事件契约

- `meta`：sessionId、agent、model、providerId、modelId、mock、contexts。
- `delta`：纯文本增量。
- `done`：sessionId、tokens、ms、model、mock、contexts。
- `error`：`{ "message": "..." }`。

前端用 `fetch` + `ReadableStream` 解析，不用原生 EventSource（无法带 Bearer Header）。

## 修改时注意

- 新建会话必须先保存 `ChatSession` 再保存 `SessionMessage`，否则 `fk_messages_session` 会失败。
- 保存助手消息后再更新轮次、Tokens、topic 和 trace。
- 历史消息只取最近 12 条，避免上下文爆炸。
- `Map.of` 不允许 null；providerId/modelId 可能为空，必须转空字符串。
- 不要在日志中打印完整提示词、用户隐私、API Key 或工具凭据。
- 未配置 DeepSeek Key 时保留本地演示流，不能启动失败。
- 非 DeepSeek 厂商适配器未接入时，应明确提示回退，不要静默伪装成绑定厂商。

## 模型绑定

Agent 的 `providerId` + `modelId` 决定运行时模型。新增厂商适配器时：

1. 在 Provider/Model 目录确认 modelId。
2. 增加模型 ID 到实际网关的映射。
3. 在 `AgentRuntimeService` 里按 provider 选择客户端。
4. 记录实际使用的模型到 `SessionMessage.model` 和 trace。

## 验证

```bash
TOKEN=$(curl -s -X POST http://localhost:8081/api/v1/auth/login ...)
curl -N -X POST http://localhost:8081/api/v1/runtime/sessions:stream   -H "Authorization: Bearer $TOKEN"   -H "Content-Type: application/json"   -d '{"agentId":"a1","message":"已签收还能退款吗？"}'
```

确认 `meta.mock=false`、`contexts>0`，并检查 `/sessions/{id}/messages` 有 user/assistant 两条记录。
