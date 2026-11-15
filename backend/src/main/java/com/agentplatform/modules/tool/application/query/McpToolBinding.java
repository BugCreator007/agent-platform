package com.agentplatform.modules.tool.application.query;

/**
 * 暴露给模型/Agent 的 MCP 工具绑定（application 层查询模型）。
 *
 * <p>{@code key} 是平台内稳定的工具键（{@code serverId.toolName}），{@code modelName}
 * 是经过字符清洗与去重后、符合模型工具名约束的名称；两者分离使工具发现同步
 * 不影响历史调用键。
 *
 * @param key         平台工具键，格式为 {@code <serverId>.<toolName>}
 * @param modelName   下发给模型的工具名（仅含字母、数字、下划线、连字符，最长 64 字符）
 * @param name        MCP 服务端声明的工具名称
 * @param description 工具描述，供模型理解用途
 * @param inputSchema 工具入参 JSON Schema 字符串
 */
public record McpToolBinding(String key, String modelName, String name, String description, String inputSchema) {
}
