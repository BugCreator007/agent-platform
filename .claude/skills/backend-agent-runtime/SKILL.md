---
name: backend-agent-runtime
description: 修改 Agent 会话运行时、SSE 流式对话、提示词组装、RAG 上下文、MCP 工具循环、会话消息持久化或 token/trace 统计时使用。
---

# Agent 运行时开发

## 先读

- `modules/chat/application/runtime/AgentRuntimeService.java`
- `modules/chat/infrastructure/ai/ModelGateway.java`
- `modules/chat/infrastructure/ai/{OpenAiCompatibleClient,AnthropicClient,ChatModelClient}.java`
- `modules/chat/application/runtime/ChatCompletionService.java`
- `modules/chat/interfaces/rest/{RuntimeController,SessionController}.java`
- `modules/chat/domain/model/{ChatSession,SessionMessage,TraceStep}.java`
- `modules/knowledge/application/service/KnowledgeApplicationService.java`
- `modules/tool/application/service/{ToolExecutionService,McpRuntimeService}.java`
- 前端 `admin-web/src/pages/Debug.tsx`、`user-web/src/api.ts`、`user-web/src/App.tsx`

## 运行时链路

```text
POST /api/v1/runtime/sessions:stream 或 /api/v1/user/chat:stream
 → AgentApplicationService.get(agentId)
 → 创建/复用会话
 → 读取最近 12 条 user/assistant 历史
 → 用户消息先落库
 → 按 agent.kb() 做 RAG 检索（失败降级为空）
 → 对 order.query 做本地关键词预取（演示用）
 → buildSystemPrompt()
 → ModelGateway.routes(providerId, modelId)
 → 有 MCP 工具：runMcpLoop()
 → 无工具：模型流式输出
 → SSE meta/delta/tool/tool-result/done/error
 → 持久化 assistant 消息、会话汇总、trace
```

## SSE 事件契约

- `meta`：`sessionId`、`agent`、`model`、`providerId`、`modelId`、`provider`、`route`、`fallbacks`、`mock`、`contexts`，有工具时 `tools`。
- `delta`：纯文本增量，`text/plain`。
- `tool`：`{name, arguments}`（MCP 工具调用开始）。
- `tool-result`：`{name, error, text}`。
- `done`：meta 字段 + `tokens`、`ms`、`mock`、`contexts`。
- `error`：`{message}`。

前端必须用 `fetch` + `ReadableStream`，因为原生 `EventSource` 不能带 Bearer Header。

## 会话与消息

- 新会话先保存 `ChatSession(status=running)`，再保存 `SessionMessage`。
- 历史只回放最近 12 条 user/assistant；工具消息不重放。
- 用户消息在模型调用前落库，失败也保留提问。
- 完成后保存 assistant 消息，并更新会话 turns/tokens/ms/topic/trace。
- token 数是字符数折半估算，不用于真实计费。
- 会话 ID 形如 `s_xxxxxxxx`，消息 ID `msg_xxxxxxxxxxxx`。

## 提示词

`buildSystemPrompt` 包含：

1. 智能体提示词。
2. 可用工具/知识库说明。
3. 本地工具结果。
4. RAG 命中片段 `[n] source #chunk: text`。
5. 中文回答、可执行下一步、不编造订单/政策的约束。

新增上下文时注意提示词长度，避免超过模型窗口。

## MCP 工具循环

- 仅当 `mcp.bindings(agent.tools())` 非空时进入。
- 最多 4 轮。
- 模型返回 `tool_calls` → 累积 ID/name/arguments → `McpRuntimeService.execute` → 写回 tool message → 再次调用模型。
- 工具失败写入错误文本，不直接中断（除非模型/网络异常）。
- SSE 额外发 `tool`、`tool-result`。
- 工具名映射使用 `binding.modelName()`，不是平台工具 key。

## 模型路由

- `ModelGateway.routes(providerId, modelId)` 由 `ModelCatalog` 解析。
- 主路由失败且尚未输出 token 时才 fallback；已输出后不切换，避免拼接不同模型回答。
- 无可用 Key 时 `ModelGateway` 返回本地演示文本，`meta.mock=true`。
- 详见 `backend-model-routing`。

## 超时与资源

- SSE 180s；单次模型 HTTP 120s；MCP 内部有各自超时。
- `onCompletion`/`onTimeout` 必须 dispose 模型订阅。
- 不要在 Servlet 请求线程中同步跑完整工具循环。
- 客户端断开时忽略二次发送异常。

## 指标

- `agent_runtime_requests_total{agent}`
- `agent_runtime_errors_total{agent}`
- `agent_runtime_duration{agent}`
- 会话成功/失败写入 `chat_sessions`，供概览统计。
- 详见 `backend-observability`。

## 当前限制

- Agent 工具循环只把 MCP 工具暴露给模型；HTTP/内置工具只用于本地预取。
- 没有 Agent ↔ Workflow 绑定；工作流不能被智能体当作工具调用。
- 没有 Agent 版本固定、长期记忆、多智能体协作。
- token 不精确，成本不是真实计费。

## 新增功能建议顺序

1. 定义 SSE 事件/状态。
2. 修改 runtime 编排与持久化。
3. 修改 admin Debug 和 user-web SSE 解析。
4. 添加 curl/真实前端验证。
5. 更新文档与测试。

## 验证

```bash
TOKEN=$(curl -s -X POST http://localhost:8081/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | jq -r .data.accessToken)

curl -N -X POST http://localhost:8081/api/v1/runtime/sessions:stream \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"agentId":"a1","message":"已签收还能退款吗？"}'
```

确认 `meta`、`delta`、`done`；有 MCP 时确认 `tool/tool-result`；检查 `/sessions/{id}/messages`。

## Checklist

- [ ] 事件契约前后端一致。
- [ ] 用户/助手消息落库。
- [ ] 历史截断与 token 估算。
- [ ] RAG/工具失败降级。
- [ ] 订阅释放。
- [ ] 不记录敏感内容。
- [ ] 管理端/用户端都验证。
- [ ] 中文注释说明状态/超时/降级。
