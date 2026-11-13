package com.agentplatform.modules.tool.interfaces.dto;

import java.util.Map;

public record McpToolCallRequest(String toolKey, Map<String, Object> arguments) {
}
