package com.agentplatform.modules.tool.domain.model;

import java.time.Instant;

public record McpToolCall(String id, String serverId, String toolKey, String toolName,
                          String requestJson, String responseJson, String status,
                          long durationMs, Instant createdAt) {
}
