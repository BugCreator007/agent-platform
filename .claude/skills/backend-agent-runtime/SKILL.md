---
name: backend-agent-runtime
description: 修改 Agent 运行时、多模型路由、流式对话、SSE、提示词组装、RAG/工具上下文或会话消息持久化时使用。
---

# Agent 运行时开发

## 先读

- `modules/chat/application/runtime/AgentRuntimeService.java`
- `modules/chat/infrastructure/ai/ModelGateway.java`
- `modules/chat/infrastructure/ai/OpenAiCompatibleClient.java`
- `modules/chat/infrastructure/ai/AnthropicClient.java`
- `modules/model/application/query/{ModelCatalog,ModelRoute}.java`
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
 → ModelCatalog 按 providerId/modelId + routing fallback 解析 ModelRoute
 → ModelGateway 按 protocol 选择 OpenAI-compatible / Anthropic 客户端
 → 主模型失败且尚未输出内容时切换备用模型
 → 发送 meta/delta/done/error（MCP 额外发送 tool/tool-result）
 → 持久化 SessionMessage 与 ChatSession
```

## SSE 事件契约

- `meta`：sessionId、agent、model、providerId、modelId、provider、route、fallbacks、mock、contexts；有 MCP 工具时带 tools。
- `delta`：纯文本增量。
- `done`：与 meta 相同的路由字段，加上 tokens、ms、mock、contexts。
- `error`：`{ "message": "..." }`。

前端用 `fetch` + `ReadableStream` 解析，不用原生 EventSource（无法带 Bearer Header）。

## 修改时注意

- 新建会话必须先保存 `ChatSession` 再保存 `SessionMessage`，否则 `fk_messages_session` 会失败。
- 保存助手消息后再更新轮次、Tokens、topic 和 trace。
- 历史消息只取最近 12 条，避免上下文爆炸。
- `Map.of` 不允许 null；providerId/modelId 可能为空，必须转空字符串。
- 不要在日志中打印完整提示词、用户隐私、API Key 或工具凭据。
- 没有任何可用 Provider Key 时保留本地演示流，不能启动失败；有 Key 时必须真实请求厂商。
- 主模型已输出 token 后不要切换备用模型，避免重复回答。
- 错误信息可以包含 provider/model/HTTP 状态，但不能包含 API Key、完整请求体或用户隐私。

## 模型绑定

Agent 的 `providerId` + `modelId` 决定运行时模型；`ModelCatalog` 再追加路由策略里的 fallback。

1. Provider 的 `protocol` 决定客户端：`openai`（DeepSeek/Kimi/千问/OpenAI/智谱）或 `anthropic`。
2. Provider 密钥加密存储，`ProviderCredentialResolver` 只对 DeepSeek 提供 `DEEPSEEK_API_KEY` 环境变量回退。
3. Model 的 `upstreamModel` 才是发给厂商的 `model` 参数；平台内 `id` 只用于绑定。
4. ModelGateway 只在主模型尚未输出任何 chunk 时切换 fallback，并把路由信息写入 meta/done/trace。
5. 新增 provider 时同步更新 `ProviderEntity`、`ModelEntity`、`V7__model_provider_routing.sql` 和前端 `Models.tsx`。

## 验证

```bash
TOKEN=$(curl -s -X POST http://localhost:8081/api/v1/auth/login ...)
curl -N -X POST http://localhost:8081/api/v1/runtime/sessions:stream   -H "Authorization: Bearer $TOKEN"   -H "Content-Type: application/json"   -d '{"agentId":"a1","message":"已签收还能退款吗？"}'
```

确认 `meta.mock=false`、`contexts>0`，并检查 `/sessions/{id}/messages` 有 user/assistant 两条记录。
