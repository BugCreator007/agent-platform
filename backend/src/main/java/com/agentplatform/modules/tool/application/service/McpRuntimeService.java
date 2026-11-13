package com.agentplatform.modules.tool.application.service;

import com.agentplatform.modules.tool.application.query.McpCallOutcome;
import com.agentplatform.modules.tool.application.query.McpToolBinding;
import com.agentplatform.modules.tool.domain.model.McpServer;
import com.agentplatform.modules.tool.domain.model.McpTool;
import com.agentplatform.modules.tool.domain.model.McpToolCall;
import com.agentplatform.modules.tool.domain.repository.McpServerRepository;
import com.agentplatform.modules.tool.domain.repository.McpToolCallRepository;
import com.agentplatform.modules.tool.domain.repository.McpToolRepository;
import com.agentplatform.modules.tool.infrastructure.mcp.McpCallResult;
import com.agentplatform.modules.tool.infrastructure.mcp.McpClientFactory;
import com.agentplatform.modules.tool.infrastructure.mcp.McpSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class McpRuntimeService {
    private final McpServerRepository servers;
    private final McpToolRepository tools;
    private final McpToolCallRepository calls;
    private final McpClientFactory factory;
    private final ObjectMapper mapper;

    public McpRuntimeService(McpServerRepository servers, McpToolRepository tools,
                             McpToolCallRepository calls, McpClientFactory factory, ObjectMapper mapper) {
        this.servers=servers; this.tools=tools; this.calls=calls; this.factory=factory; this.mapper=mapper;
    }

    public List<McpToolBinding> bindings(List<String> keys) {
        List<McpToolBinding> result = new ArrayList<>();
        Map<String, String> used = new HashMap<>();
        for (McpTool tool : tools.findByKeys(keys)) {
            if (!tool.enabled()) continue;
            String modelName = modelName(tool.toolKey());
            String base = modelName;
            int i = 2;
            while (used.containsValue(modelName)) modelName = base.substring(0, Math.min(base.length(), 56)) + "_" + i++;
            used.put(tool.toolKey(), modelName);
            result.add(new McpToolBinding(tool.toolKey(), modelName, tool.name(), tool.description(), tool.inputSchema()));
        }
        return result;
    }

    public McpCallOutcome execute(String toolKey, Map<String, Object> arguments) {
        McpTool tool = tools.findByKey(toolKey)
                .orElseThrow(() -> new IllegalArgumentException("MCP 工具不存在: " + toolKey));
        if (!tool.enabled()) throw new IllegalStateException("MCP 工具已停用: " + toolKey);
        McpServer server = servers.findById(tool.serverId())
                .orElseThrow(() -> new IllegalArgumentException("MCP 服务不存在: " + tool.serverId()));
        if (!server.enabled()) throw new IllegalStateException("MCP 服务已停用: " + server.name());
        long start = System.currentTimeMillis();
        String status = "SUCCESS";
        String response = null;
        String text = null;
        boolean error = false;
        try (McpSession session = factory.open(server)) {
            McpCallResult result = session.callTool(tool.name(), arguments == null ? Map.of() : arguments);
            error = result.error();
            text = result.text();
            response = result.rawJson();
            status = error ? "ERROR" : "SUCCESS";
        } catch (Exception e) {
            status = "FAILED";
            text = e.getMessage();
            error = true;
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            try {
                calls.save(new McpToolCall("call_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                        server.id(), toolKey, tool.name(), mapper.writeValueAsString(arguments), response,
                        status, elapsed, Instant.now()));
            } catch (Exception ignored) { }
        }
        return new McpCallOutcome(error, text);
    }

    private String modelName(String key) {
        String sanitized = key.replaceAll("[^a-zA-Z0-9_-]", "_");
        if (sanitized.length() <= 64) return sanitized;
        return sanitized.substring(0, 55) + "_" + Math.abs(key.hashCode());
    }
}
