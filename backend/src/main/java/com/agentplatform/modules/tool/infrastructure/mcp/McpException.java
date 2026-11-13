package com.agentplatform.modules.tool.infrastructure.mcp;

public class McpException extends RuntimeException {
    public McpException(String message) { super(message); }
    public McpException(String message, Throwable cause) { super(message, cause); }
}
