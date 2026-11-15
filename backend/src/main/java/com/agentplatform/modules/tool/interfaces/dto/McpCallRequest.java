package com.agentplatform.modules.tool.interfaces.dto;

import java.util.Map;

/**
 * MCP 工具调用请求（interfaces 层 DTO）。
 *
 * <p>对应 {@code POST /api/v1/mcp-tools:call} 的请求体。
 *
 * @param arguments 传给 MCP 工具的参数键值对；可为 {@code null}，服务端按空参数处理
 */
public record McpCallRequest(Map<String, Object> arguments) {
}
