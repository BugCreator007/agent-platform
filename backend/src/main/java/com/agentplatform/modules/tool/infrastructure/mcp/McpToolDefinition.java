package com.agentplatform.modules.tool.infrastructure.mcp;

/**
 * MCP 工具定义（infrastructure 层传输模型）。
 *
 * <p>对应 {@code tools/list} 响应中的单个工具条目，是同步到平台工具目录的原始数据。
 *
 * @param name        MCP 服务端工具名（通常不含命名空间）
 * @param description 工具描述，缺失时为空字符串
 * @param inputSchema 入参 JSON Schema 字符串，缺失时回退为 {@code {"type":"object"}}
 */
public record McpToolDefinition(String name, String description, String inputSchema) {
}
