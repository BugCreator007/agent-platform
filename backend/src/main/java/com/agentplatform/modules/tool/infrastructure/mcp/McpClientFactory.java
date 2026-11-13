package com.agentplatform.modules.tool.infrastructure.mcp;

import com.agentplatform.modules.tool.domain.model.McpServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class McpClientFactory {
    private final ObjectMapper mapper;
    private final Set<String> allowedCommands;

    public McpClientFactory(ObjectMapper mapper,
                            @Value("${app.mcp.stdio.allowed-commands:npx,node,python,python3,uvx}") String allowedCommands) {
        this.mapper = mapper;
        this.allowedCommands = Arrays.stream(allowedCommands.split(",")).map(String::trim)
                .filter(s -> !s.isBlank()).collect(Collectors.toSet());
    }

    public McpSession open(McpServer server) {
        String transport = server.transport() == null ? "" : server.transport().trim().toLowerCase();
        return switch (transport) {
            case "http", "streamable-http", "streamable_http" -> new StreamableHttpMcpSession(server, mapper).open();
            case "sse" -> new SseMcpSession(server, mapper).open();
            case "stdio" -> new StdioMcpSession(server, mapper, allowedCommands).open();
            default -> throw new McpException("不支持的 MCP 传输方式: " + server.transport());
        };
    }
}
