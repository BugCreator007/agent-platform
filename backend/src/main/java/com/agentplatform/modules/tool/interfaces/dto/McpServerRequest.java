package com.agentplatform.modules.tool.interfaces.dto;

public record McpServerRequest(String name, String transport, String url, String command, String args,
                               String authType, String authToken, Boolean enabled) {
}
