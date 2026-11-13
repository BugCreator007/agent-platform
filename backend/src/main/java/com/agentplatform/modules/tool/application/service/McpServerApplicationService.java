package com.agentplatform.modules.tool.application.service;

import com.agentplatform.common.error.BusinessException;
import com.agentplatform.common.error.ErrorCode;
import com.agentplatform.modules.tool.application.query.McpCallOutcome;
import com.agentplatform.modules.tool.application.query.McpServerView;
import com.agentplatform.modules.tool.application.query.McpToolView;
import com.agentplatform.modules.tool.domain.model.McpServer;
import com.agentplatform.modules.tool.domain.model.McpTool;
import com.agentplatform.modules.tool.domain.repository.McpServerRepository;
import com.agentplatform.modules.tool.domain.repository.McpToolCallRepository;
import com.agentplatform.modules.tool.domain.repository.McpToolRepository;
import com.agentplatform.modules.tool.domain.repository.ToolRepository;
import com.agentplatform.modules.tool.infrastructure.mcp.McpClientFactory;
import com.agentplatform.modules.tool.infrastructure.mcp.McpSession;
import com.agentplatform.modules.tool.infrastructure.mcp.McpToolDefinition;
import com.agentplatform.modules.tool.infrastructure.mcp.McpUrlValidator;
import com.agentplatform.modules.tool.interfaces.dto.McpServerRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class McpServerApplicationService {
    private final McpServerRepository servers;
    private final McpToolRepository tools;
    private final McpToolCallRepository calls;
    private final ToolRepository catalog;
    private final McpClientFactory factory;
    private final McpRuntimeService runtime;
    private final McpUrlValidator urlValidator;

    public McpServerApplicationService(McpServerRepository servers, McpToolRepository tools,
                                       McpToolCallRepository calls, ToolRepository catalog,
                                       McpClientFactory factory, McpRuntimeService runtime,
                                       McpUrlValidator urlValidator) {
        this.servers=servers; this.tools=tools; this.calls=calls; this.catalog=catalog;
        this.factory=factory; this.runtime=runtime; this.urlValidator=urlValidator;
    }

    public List<McpServerView> list() {
        return servers.findAll().stream().map(this::view).toList();
    }

    public McpServerView get(String id) { return view(require(id)); }

    public McpServerView create(McpServerRequest request) {
        String name = request.name() == null ? "" : request.name().trim();
        if (name.isBlank()) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "MCP 服务名称不能为空");
        String transport = normalizeTransport(request.transport());
        validateEndpoint(transport, request.url(), request.command());
        McpServer server = new McpServer("mcp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10),
                name, transport, request.url(), request.command(), request.args(),
                request.authType() == null || request.authType().isBlank() ? "bearer" : request.authType(),
                request.authToken(), request.enabled() == null || request.enabled(), "DISCONNECTED", null, null);
        servers.save(server);
        return view(server);
    }

    public McpServerView update(String id, McpServerRequest request) {
        McpServer server = require(id);
        String transport = request.transport() == null ? server.transport() : normalizeTransport(request.transport());
        validateEndpoint(transport, request.url() == null ? server.url() : request.url(),
                request.command() == null ? server.command() : request.command());
        server.update(request.name(), transport, request.url(), request.command(), request.args(),
                request.authType(), request.authToken(), request.enabled());
        server.disconnected();
        servers.save(server);
        return view(server);
    }

    public List<McpToolView> tools(String serverId) {
        require(serverId);
        return tools.findByServer(serverId).stream().map(this::toolView).toList();
    }

    public McpServerView connect(String id) {
        McpServer server = require(id);
        try (McpSession session = factory.open(server)) {
            List<McpToolDefinition> discovered = session.listTools();
            syncTools(server, discovered);
            server.connected();
            servers.save(server);
            return view(server);
        } catch (Exception e) {
            server.failed(e.getMessage());
            servers.save(server);
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "MCP 连接失败: " + e.getMessage());
        }
    }

    public McpServerView sync(String id) { return connect(id); }

    public McpCallOutcome call(String toolKey, Map<String, Object> arguments) {
        return runtime.execute(toolKey, arguments);
    }

    public McpToolView toggleTool(String id) {
        McpTool tool = tools.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "MCP 工具不存在"));
        tool.toggle();
        tools.save(tool);
        catalog.findByName(tool.toolKey()).ifPresent(t -> {
            if (t.enabled() != tool.enabled()) catalog.save(new com.agentplatform.modules.tool.domain.model.Tool(
                    t.name(), t.type(), t.owner(), t.calls(), t.errorRate(), t.health(), t.checked(),
                    tool.enabled(), t.description(), t.endpoint()));
        });
        return toolView(tool);
    }

    @Transactional
    public void delete(String id) {
        McpServer server = require(id);
        for (McpTool tool : tools.findByServer(id)) catalog.deleteByName(tool.toolKey());
        tools.deleteByServer(id);
        servers.deleteById(id);
    }

    public List<?> recentCalls(String id, int limit) {
        require(id);
        return calls.recent(id, limit);
    }

    private void syncTools(McpServer server, List<McpToolDefinition> discovered) {
        for (McpToolDefinition definition : discovered) {
            String key = server.id() + "." + definition.name();
            McpTool existing = tools.findByKey(key).orElse(null);
            String id = existing == null ? "mt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) : existing.id();
            McpTool tool = new McpTool(id, server.id(), key, definition.name(), definition.description(),
                    definition.inputSchema(), existing == null || existing.enabled(), risk(definition.name()));
            tools.save(tool);
            catalog.save(new com.agentplatform.modules.tool.domain.model.Tool(
                    key, "MCP", server.name(), 0, 0, "healthy", "刚刚", tool.enabled(),
                    definition.description(), server.url()));
        }
    }

    private String risk(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.contains("delete") || n.contains("remove") || n.contains("write") || n.contains("update") ? "write" : "read";
    }

    private String normalizeTransport(String transport) {
        String t = transport == null ? "streamable-http" : transport.trim().toLowerCase(Locale.ROOT);
        if (t.equals("http") || t.equals("streamable_http")) return "streamable-http";
        if (!t.equals("streamable-http") && !t.equals("sse") && !t.equals("stdio")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持的 MCP 传输方式: " + transport);
        }
        return t;
    }

    private void validateEndpoint(String transport, String url, String command) {
        if ("stdio".equals(transport)) {
            if (command == null || command.isBlank()) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "stdio 需要启动命令");
        } else {
            if (url == null || url.isBlank()) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "HTTP/SSE 传输需要 URL");
            try { urlValidator.validate(url); } catch (IllegalArgumentException e) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, e.getMessage());
            }
        }
    }

    private McpServer require(String id) {
        return servers.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "MCP 服务不存在: " + id));
    }

    private McpServerView view(McpServer s) {
        return new McpServerView(s.id(), s.name(), s.transport(), s.url(), s.command(), s.args(), s.authType(),
                s.enabled(), s.status(), s.lastError(), s.lastConnectedAt(), tools.findByServer(s.id()).size());
    }

    private McpToolView toolView(McpTool t) {
        return new McpToolView(t.id(), t.serverId(), t.toolKey(), t.name(), t.description(), t.inputSchema(),
                t.enabled(), t.riskLevel());
    }
}
