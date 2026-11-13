---
name: backend-mcp-integration
description: 接入或实现 MCP 服务客户端、工具发现、tools/call、传输层和 Agent 工具调用循环时使用。
---

# MCP 接入开发

## 当前状态

项目已有 MCP 工具注册的 UI 和 `tools` 元数据，但尚未实现完整 MCP Client。接入真实 MCP 服务时不要只保存 URL。

## 传输选择

- TKE/腾讯云：优先 Streamable HTTP（`https://host/mcp`）或 SSE。
- 本地：stdio（`npx ...`、`python ...`）。
- 生产不要把可写 MCP 服务直接暴露公网。

## MCP 握手

```text
initialize
 → notifications/initialized
 → tools/list
 → 保存工具 schema
使用：
 → tools/call
```

`tools/list` 返回工具名、描述和 `inputSchema`；`tools/call` 传入 `name` + `arguments`。

## 建议新增

```text
mcp_server(id,name,transport,url,command,args,auth_type,secret_ref,status,last_connected)
mcp_tool(id,server_id,name,description,input_schema,enabled,risk_level)
mcp_tool_call(id,server_id,tool_name,request_json,response_json,status,duration_ms,created_at)
```

接口：

```text
POST /api/v1/mcp-servers
POST /api/v1/mcp-servers/{id}/connect
GET  /api/v1/mcp-servers/{id}/tools
POST /api/v1/mcp-servers/{id}/tools/{tool}:call
```

## Agent 工具循环

1. 加载智能体绑定的 MCP 工具。
2. 将 `inputSchema` 转为模型 tools 定义。
3. DeepSeek 返回 `tool_calls` 时暂停文本生成。
4. ToolRouter 调用 MCP `tools/call`。
5. 将结果作为 tool message 回传模型，继续生成。
6. 所有调用写审计，写工具需要人工审批。

## 安全

- URL/命令白名单。
- 禁止内网/元数据地址，除非显式配置。
- Token 加密存储（KMS/SSM），不写日志。
- 超时、重试、限流、响应大小限制。
- 工具输出视为不可信，防提示词注入。
- 读/写/删除工具分级；写操作二次确认。

## 临时替代

如果暂时不改 MCP Client，可在 MCP 服务外提供 REST 包装，再注册为 `type=HTTP` 工具；工作流 `ToolExecutionService` 可调用，但 Agent 通用工具循环仍需补齐。
