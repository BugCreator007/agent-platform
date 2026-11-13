---
name: backend-mcp-integration
description: 修改 MCP 服务接入、Streamable HTTP/SSE/stdio 客户端、工具发现、tools/call、MCP 工具目录或 Agent 工具调用循环时使用。
---

# MCP 接入开发

## 当前实现

MCP 已经落到 `tool` 模块，运行时可用：

- Streamable HTTP
- SSE
- stdio
- `initialize`
- `notifications/initialized`
- `tools/list`
- `tools/call`
- MCP Token AES-GCM 加密
- 工具目录同步到 `tools` 表
- Agent 运行时 function calling 循环

## 先读

- `modules/tool/infrastructure/mcp/`
  - `McpClientFactory`
  - `StreamableHttpMcpSession`
  - `SseMcpSession`
  - `StdioMcpSession`
  - `McpUrlValidator`
- `modules/tool/application/service/McpServerApplicationService.java`
- `modules/tool/application/service/McpRuntimeService.java`
- `modules/tool/interfaces/rest/McpController.java`
- `modules/chat/application/runtime/AgentRuntimeService.java`
- `modules/chat/infrastructure/ai/DeepSeekClient.java`
- 前端 `pages/Tools.tsx`

## 数据表

```text
mcp_servers       服务配置、传输方式、加密 Token、连接状态
mcp_tools         发现到的工具、inputSchema、风险等级
mcp_tool_calls    调用请求/响应、状态、耗时
```

迁移：`V5__mcp_servers.sql`、`V6__mcp_server_enabled.sql`。

## 管理接口

```text
GET    /api/v1/mcp-servers
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

连接成功后会：

1. 执行 `initialize`。
2. 发送 `notifications/initialized`。
3. 执行 `tools/list`。
4. 将工具写入 `mcp_tools`。
5. 以 `<serverId>.<toolName>` 为 key 同步到 `tools` 目录，供智能体绑定。

## Agent 工具循环

`AgentRuntimeService` 检测到 Agent 绑定了 MCP 工具后：

```text
构建 tools schema
 → DeepSeek streamWithTools
 → 解析 delta.tool_calls
 → McpRuntimeService.execute
 → 写入 tool message
 → 再次调用模型
 → 流式输出最终回答
```

最多 4 轮工具循环。SSE 额外发送 `tool` 和 `tool-result` 事件。

## 传输注意事项

- Streamable HTTP：优先使用；需要 `Content-Type: application/json`、`Accept: application/json, text/event-stream`，并保存 `Mcp-Session-Id`。
- SSE：先 GET 获取 `endpoint` 事件，再向 endpoint POST；响应通过 SSE `message` 事件返回。
- stdio：只允许 `MCP_STDIO_ALLOWED_COMMANDS` 中的命令；不要拼接 shell 字符串，使用 `ProcessBuilder`。
- 内部服务通常使用内网地址；`MCP_ALLOW_PRIVATE_URLS=false` 时禁止内网/本机。生产优先用网络策略和内部 CLB。

## 安全

- 不返回或打印 MCP Token。
- Token 使用 `SecretCipher` 加密入库。
- URL 只允许 http/https，拒绝链路本地/任意地址。
- 工具输出视为不可信，防止提示词注入。
- 写工具（riskLevel=write）目前会在工具列表中标记，但 Agent 自动执行；生产应增加人工审批。
- 所有管理写操作由审计过滤器记录；MCP 调用记录写入 `mcp_tool_calls`。

## 验证

```bash
# 连接并同步
curl -X POST http://localhost:8081/api/v1/mcp-servers/{id}/connect \
  -H "Authorization: Bearer <token>"

# 直接调用
curl -X POST http://localhost:8081/api/v1/mcp-tools:call \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"toolKey":"mcp_xxx.weather.get","arguments":{"city":"上海"}}'
```

已用 mock MCP 服务验证 Streamable HTTP、SSE、stdio 三种传输，以及 Agent 自动工具调用。
