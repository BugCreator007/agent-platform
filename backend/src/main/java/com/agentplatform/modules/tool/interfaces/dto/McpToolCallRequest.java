package com.agentplatform.modules.tool.interfaces.dto;

import java.util.Map;

/**
 * 按工具键调用 MCP 工具的请求（interfaces 层 DTO）。
 *
 * <p>同时携带平台工具键与参数，供管理端直接调试某个已同步的 MCP 工具。
 *
 * @param toolKey   平台工具键，格式为 {@code <serverId>.<toolName>}
 * @param arguments 工具入参键值对；可为 {@code null}
 */
public record McpToolCallRequest(String toolKey, Map<String, Object> arguments) {
}
