package com.agentplatform.modules.tool.infrastructure.mcp;

import java.util.List;
import java.util.Map;

public interface McpSession extends AutoCloseable {
    List<McpToolDefinition> listTools();
    McpCallResult callTool(String name, Map<String, Object> arguments);
    @Override void close();
}
