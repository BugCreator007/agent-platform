package com.agentplatform.modules.tool.infrastructure.mcp;

public record McpCallResult(boolean error, String text, String rawJson) {
}
