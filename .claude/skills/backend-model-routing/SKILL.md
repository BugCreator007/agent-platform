---
name: backend-model-routing
description: 修改模型厂商、模型目录、Provider 密钥、OpenAI-compatible/Anthropic 协议、主模型与 fallback 路由、Provider 连通性测试时使用。
---

# 多模型路由开发

## 先读

- `modules/model/domain/model/{Provider,ModelInfo,RoutingPolicy}.java`
- `modules/model/application/query/{ModelCatalog,ModelRoute,ProviderTestResult}.java`
- `modules/model/application/service/{ModelApplicationService,ProviderCredentialResolver,ProviderProbeService}.java`
- `modules/model/infrastructure/persistence/...`
- `modules/chat/infrastructure/ai/{ModelGateway,OpenAiCompatibleClient,AnthropicClient,ChatModelClient}.java`
- `modules/model/interfaces/rest/ModelController.java`
- `admin-web/src/pages/Models.tsx`、`admin-web/src/pages/Agents.tsx`
- `V7__model_provider_routing.sql`

## 领域模型

### Provider

- `id/name/status/endpoint/protocol`
- `quota/used/calls/latency/success`
- `key`：脱敏展示值
- `apiKey`：解密后的真实值，`@JsonIgnore`，不得返回
- `keyConfigured`
- `protocol`：`openai` 或 `anthropic`
- 操作：`updateKey`、`markTested`、`markFailed`、`toggle`、`updateQuota`

### ModelInfo

- `id`：平台内部 ID，用于智能体绑定。
- `providerId`
- `upstreamModel`：真正发送给厂商的 `model` 参数。
- `context/inPrice/outPrice/capabilities/status/routeWeight/isDefault`

### RoutingPolicy

- `strategy`：failover/cost/latency/quality
- `primary`：模型 ID
- `fallback`：模型 ID 列表
- `region`

## 密钥

- `ProviderEntity` 使用 `SecretCipher` 加密 `api_key_encrypted`。
- `ProviderCredentialResolver`：
  1. 数据库配置的 Key 优先。
  2. DeepSeek 未配置时回退 `app.ai.deepseek.api-key` / `DEEPSEEK_API_KEY`。
- 更新密钥：`PUT /api/v1/providers/{id}/key`。
- 密钥只显示掩码，不复制明文。
- 切换 `SECRETS_KEY` 后历史密文不可解密，需要重新录入。

## ModelCatalog 路由解析

`ModelCatalog.routes(providerId, modelId)`：

1. 优先解析智能体明确绑定的模型。
2. 若 modelId 为空，按 providerId 找该厂商首个可用模型。
3. 若 provider/model 都为空，使用路由策略 primary。
4. 追加 routing.fallback。
5. 去除重复、保留顺序。
6. 无候选时回退默认模型/首个模型。

`defaultRoutes()` 按策略排序：

- failover：主模型 → fallback 顺序。
- cost：输入+输出价格升序。
- latency：解析 latency 数值升序。
- quality：routeWeight 降序。

`ModelRoute.available()` 要求 API Key 非空、Provider/Model 不是 paused。

## ModelGateway

- 按 `protocol` 选择客户端：
  - `anthropic` → `AnthropicClient`（Messages API）
  - 其他 → `OpenAiCompatibleClient`（/chat/completions）
- 主路由失败时，只有**尚未输出任何 token** 才尝试 fallback。
- 已输出文本后失败直接报错，避免拼接不同模型。
- 无可用路由时返回本地演示文本，`mock=true`。
- 同步 `complete()` 供工作流/摘要使用。

## OpenAI-compatible 客户端

- 支持 DeepSeek、Kimi、千问/DashScope、OpenAI、智谱。
- Endpoint 约定为 API Base，例如：
  - DeepSeek `https://api.deepseek.com/v1`
  - Kimi `https://api.moonshot.cn/v1`
  - 千问 `https://dashscope.aliyuncs.com/compatible-mode/v1`
  - 智谱 `https://open.bigmodel.cn/api/paas/v4`
- Authorization: Bearer。
- DeepSeek 额外发送 `thinking: {type: disabled}`。
- 解析 SSE `choices[0].delta.content` 和 `delta.tool_calls`。
- 工具定义使用 OpenAI `{type:function,function:{name,description,parameters}}`。

## Anthropic 客户端

- `POST /messages`
- headers：`x-api-key`、`anthropic-version: 2023-06-01`
- body：`model/max_tokens/system/messages/stream/tools`
- SSE 事件：
  - `content_block_delta` → `text_delta` 文本
  - `tool_use` + `input_json_delta` → 累积工具调用
  - `message_stop` → 输出 tool call 片段并结束
- Anthropic 工具 schema 是 `{name,description,input_schema}`。
- 工具结果以 user + `tool_result` content 回传。

## Provider 连通性测试

`ProviderProbeService`：

- 无 Key → 返回失败并把 Provider 标为 degraded。
- 无模型 → 提示先添加模型。
- OpenAI-compatible：用首个非 paused 模型做一次 `max_tokens=1` 的 `/chat/completions`。
- Anthropic：`/messages`，max_tokens=1。
- 成功更新 latency/status=healthy；失败 status=degraded。
- 返回 `ProviderTestResult(ok,status,latency,message)`。

## API

```text
GET    /api/v1/providers
POST   /api/v1/providers
PUT    /api/v1/providers/{id}/quota
PUT    /api/v1/providers/{id}/key
POST   /api/v1/providers/{id}/test
POST   /api/v1/providers/{id}/toggle
DELETE /api/v1/providers/{id}

GET    /api/v1/models
POST   /api/v1/models
DELETE /api/v1/models/{id}
POST   /api/v1/models/{id}/default
GET    /api/v1/models/routing
PUT    /api/v1/models/routing
```

默认模型与 routing.primary 会双向同步：`setDefault` 更新路由主模型；`updateRouting` 更新默认标记。

## 种子数据

- Provider：Anthropic、千问 AI 平台、DeepSeek、Kimi、OpenAI、智谱。
- 模型：Claude、Qwen、DeepSeek、Kimi、GPT、GLM。
- `ModelDataSeeder` 首次全量；已有库增量补充 Kimi/新增 Qwen 模型。
- DeepSeek 从环境变量回填密钥。

## 新增厂商/模型

1. 选择协议：OpenAI-compatible 或 Anthropic。
2. 配置 endpoint、API Key。
3. 添加模型，填写平台 ID、上游模型 ID、价格、上下文。
4. 测试连接。
5. 在智能体编辑页绑定 providerId/modelId。
6. 配置路由 fallback。
7. 用真实调用验证 `meta.route`/`done`。

## 当前限制

- Provider calls/used/quota 主要是展示字段，未按真实调用累加。
- 没有 Provider 级限流、预算硬限制。
- 没有模型目录自动发现/定期同步。
- 成本/用量不是真实计费。
- 没有模型版本历史。
- Kimi/千问需要用户配置各自 Key。

## Checklist

- [ ] 新协议有客户端与 SSE 解析。
- [ ] upstreamModel 与平台 modelId 分离。
- [ ] 密钥加密、掩码、无日志。
- [ ] 路由可用性/fallback 顺序正确。
- [ ] Provider 测试真实请求。
- [ ] 前端 Models/Agents 契约同步。
- [ ] 真实或 mock 调用验证。
- [ ] 中文注释说明 fallback 语义。
