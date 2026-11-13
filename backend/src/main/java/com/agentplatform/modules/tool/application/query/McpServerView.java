package com.agentplatform.modules.tool.application.query;

import java.time.Instant;

public record McpServerView(String id, String name, String transport, String url, String command,
                            String args, String authType, boolean enabled, String status,
                            String lastError, Instant lastConnectedAt, long toolCount) {
}
