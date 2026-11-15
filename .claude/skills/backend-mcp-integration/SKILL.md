---
name: backend-mcp-integration
description: 修改 MCP 服务接入、Streamable HTTP/SSE/stdio 客户端、initialize/tools/list/tools/call、工具目录同步、MCP 鉴权或 Agent 工具循环时使用。
---

# MCP 接入开发

## 先读

- `modules/tool/infrastructure/mcp/`
  - `McpClientFactory`
  - `StreamableHttpMcpSession`
  - `SseMcpSession`
  - `StdioMcpSession`
  - `McpUrlValidator`
  - `McpSession/McpToolDefinition/McpCallResult`
- `modules/tool/application/service/{McpServerApplicationService,McpRuntimeService}.java`
- `modules/tool/infrastructure/persistence/entity/McpServerEntity.java`
- `modules/tool/interfaces/rest/McpController.java`
- `modules/chat/application/runtime/AgentRuntimeService.java`
- `admin-web/src/pages/Tools.tsx`

## 数据表

```text
mcp_servers     id/name/transport/url/command/args/auth_type/secret_encrypted/status/enabled
mcp_tools       server_id/tool_key/name/description/input_schema/enabled/risk_level
mcp_tool_calls  server_id/tool_key/tool_name/request_json/response_json/status/duration_ms
```

迁移：`V5__mcp_servers.sql`、`V6__mcp_server_enabled.sql`。

## 传输

### Streamable HTTP

- `POST {url}`，`Content-Type: application/json`，`Accept: application/json, text/event-stream`。
- 保存响应头 `Mcp-Session-Id`，后续请求带上。
- 支持 JSON 或 SSE 响应。

### SSE（legacy）

- 先 `GET` SSE endpoint，等待 `event: endpoint` 获取 message endpoint。
- 后续 `POST` 到该 endpoint。
- 响应通过 SSE `message` 事件返回。

### stdio

- `ProcessBuilder` 启动命令，逐行 JSON-RPC。
- 只允许 `MCP_STDIO_ALLOWED_COMMANDS` 中的命令（默认 npx/node/python/python3/uvx）。
- 禁止拼接 shell 字符串，参数以数组传递。

## JSON-RPC 生命周期

```text
initialize
 → notifications/initialized
 → tools/list
 → tools/call
```

- 请求 ID 自增，响应按 ID 匹配。
- 工具发现写入 `mcp_tools`。
- 工具目录同步到 `tools` 表，key 为 `<serverId>.<toolName>`。
- Agent 绑定工具后，只有 MCP 工具进入 function calling。

## 安全

- Token 使用 `SecretCipher` 加密到 `secret_encrypted`。
- API 视图不返回 Token。
- `McpUrlValidator` 限制 http/https，阻止 metadata/link-local；`MCP_ALLOW_PRIVATE_URLS=false` 时阻止内网/localhost。
- stdio 命令白名单。
- 工具输出视为不可信，防提示词注入。
- 写工具（riskLevel=write）目前不会自动人工审批——这是待补能力。
- 不在日志中输出 Token、Cookie、完整工具参数。

## 管理 API

```text
GET    /api/v1/mcp-servers
GET    /api/v1/mcp-servers/{id}
POST   /api/v1/mcp-servers
PUT    /api/v1/mcp-servers/{id}
DELETE /api/v1/mcp-servers/{id}
POST   /api/v1/mcp-servers/{id}/connect
POST   /api/v1/mcp-servers/{id}/sync
GET    /api/v1/mcp-servers/{id}/tools
GET    /api/v1/mcp-servers/{id}/calls
PUT    /api/v1/mcp-tools/{id}/toggle
POST   /api/v1/mcp-tools:call
```

连接成功后执行 initialize → initialized → tools/list → 写目录。

## Agent 工具循环

`AgentRuntimeService`：

```text
mcp.bindings(agent.tools())
 → tool schema
 → model.streamWithTools
 → 解析 delta.tool_calls
 → McpRuntimeService.execute
 → 写 tool message
 → 最多 4 轮
 → 最终回答
```

SSE 事件：`tool`、`tool-result`。

## 验证

Mock server 可验证三种传输：

```bash
# Streamable HTTP 示例
curl -X POST http://localhost:8081/api/v1/mcp-servers \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"mock","transport":"streamable-http","url":"http://127.0.0.1:8765/mcp","authType":"none"}'
```

连接后 `sync`，再 `mcp-tools:call`。stdio 需要本机允许命令。

## 当前限制

- OAuth 授权流程未实现。
- MCP Resources/Prompts 未实现。
- 写工具人工审批未实现。
- 周期健康检查/失效工具清理未实现。
- MCP 工具调用审计有记录，但没有完整重试/幂等。
- 没有自动化 MCP 测试套件，主要靠 mock 手工验证。

## Checklist

- [ ] transport 客户端协议正确。
- [ ] initialize/session id/SSE 解析正确。
- [ ] Token 加密、脱敏、无日志。
- [ ] URL/stdio 安全限制。
- [ ] 工具目录同步与停用。
- [ ] Agent 工具循环最多轮次与错误处理。
- [ ] mock 三传输验证。
- [ ] 中文注释。
