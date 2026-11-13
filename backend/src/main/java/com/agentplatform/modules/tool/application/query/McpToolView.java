package com.agentplatform.modules.tool.application.query;

public record McpToolView(String id, String serverId, String toolKey, String name,
                          String description, String inputSchema, boolean enabled, String riskLevel) {
}
