package com.agentplatform.modules.tool.application.query;

/**
 * MCP 工具管理视图（跨模块可见的只读投影）。
 *
 * <p>属于应用层查询模型，用于管理端展示某 MCP 服务下已同步的工具及其风险级别。
 *
 * @param id          MCP 工具主键，形如 {@code mt_xxxxxxxxxxxx}
 * @param serverId    所属 MCP 服务 ID
 * @param toolKey     平台工具键，格式为 {@code <serverId>.<toolName>}
 * @param name        MCP 服务端声明的工具名称
 * @param description 工具描述
 * @param inputSchema 工具入参 JSON Schema 字符串
 * @param enabled     工具是否启用（停用后不会下发给模型，也不能被调用）
 * @param riskLevel   风险级别：{@code read} 或 {@code write}（按工具名关键字推断）
 */
public record McpToolView(String id, String serverId, String toolKey, String name,
                          String description, String inputSchema, boolean enabled, String riskLevel) {
}
