package com.agentplatform.modules.tool.infrastructure.mcp;

import com.agentplatform.modules.tool.domain.model.McpServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class StreamableHttpMcpSession implements McpSession {
    private static final Logger log = LoggerFactory.getLogger(StreamableHttpMcpSession.class);
    private final McpServer server;
    private final ObjectMapper mapper;
    private final HttpClient client;
    private final AtomicLong ids = new AtomicLong(1);
    private String sessionId;

    public StreamableHttpMcpSession(McpServer server, ObjectMapper mapper) {
        this.server = server;
        this.mapper = mapper;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public McpSession open() {
        if (!server.enabled()) throw new McpException("MCP 服务已停用");
        JsonNode result = request("initialize", Map.of(
                "protocolVersion", "2025-03-26",
                "capabilities", Map.of(),
                "clientInfo", Map.of("name", "agent-platform", "version", "1.0")));
        if (result == null || result.path("error").isMissingNode() == false) {
            throw new McpException("MCP initialize 失败: " + (result == null ? "empty response" : result.path("error").toString()));
        }
        notify("notifications/initialized", Map.of());
        return this;
    }

    @Override
    public List<McpToolDefinition> listTools() {
        JsonNode response = request("tools/list", Map.of());
        JsonNode tools = response.path("result").path("tools");
        List<McpToolDefinition> definitions = new ArrayList<>();
        if (tools.isArray()) {
            for (JsonNode tool : tools) {
                definitions.add(new McpToolDefinition(tool.path("name").asText(),
                        tool.path("description").asText(""),
                        tool.path("inputSchema").isMissingNode() ? "{\"type\":\"object\"}" : tool.path("inputSchema").toString()));
            }
        }
        return definitions;
    }

    @Override
    public McpCallResult callTool(String name, Map<String, Object> arguments) {
        JsonNode response = request("tools/call", Map.of("name", name, "arguments", arguments == null ? Map.of() : arguments));
        JsonNode error = response.path("error");
        if (!error.isMissingNode()) {
            return new McpCallResult(true, error.path("message").asText("MCP tool call failed"), response.toString());
        }
        JsonNode result = response.path("result");
        StringBuilder text = new StringBuilder();
        JsonNode content = result.path("content");
        if (content.isArray()) {
            for (JsonNode item : content) {
                if ("text".equals(item.path("type").asText()) || item.path("text").isTextual()) {
                    text.append(item.path("text").asText()).append('\n');
                } else {
                    text.append(item.toString()).append('\n');
                }
            }
        } else if (result.isTextual()) {
            text.append(result.asText());
        }
        boolean isError = result.path("isError").asBoolean(false);
        return new McpCallResult(isError, text.toString().trim(), response.toString());
    }

    private JsonNode request(String method, Object params) {
        long id = ids.getAndIncrement();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jsonrpc", "2.0");
        body.put("id", id);
        body.put("method", method);
        body.put("params", params);
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(server.url()))
                    .timeout(Duration.ofSeconds(45))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json, text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            applyAuth(builder);
            if (sessionId != null && !sessionId.isBlank()) builder.header("Mcp-Session-Id", sessionId);
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            response.headers().firstValue("Mcp-Session-Id").ifPresent(value -> sessionId = value);
            if (response.statusCode() / 100 != 2) {
                throw new McpException("MCP HTTP " + response.statusCode() + ": " + response.body());
            }
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            JsonNode node = contentType.contains("text/event-stream")
                    ? parseSse(response.body(), id)
                    : mapper.readTree(response.body());
            if (node == null) throw new McpException("MCP 响应为空: " + method);
            JsonNode error = node.path("error");
            if (!error.isMissingNode()) {
                throw new McpException("MCP " + method + " 失败: " + error.path("message").asText(error.toString()));
            }
            return node;
        } catch (McpException e) {
            throw e;
        } catch (Exception e) {
            throw new McpException("MCP 请求失败: " + e.getMessage(), e);
        }
    }

    private void notify(String method, Object params) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jsonrpc", "2.0");
        body.put("method", method);
        body.put("params", params);
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(server.url()))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            applyAuth(builder);
            if (sessionId != null && !sessionId.isBlank()) builder.header("Mcp-Session-Id", sessionId);
            client.send(builder.build(), HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            log.debug("MCP notification {} failed: {}", method, e.getMessage());
        }
    }

    private JsonNode parseSse(String body, long id) throws Exception {
        if (body == null) return null;
        JsonNode last = null;
        for (String line : body.split("\\R")) {
            if (!line.startsWith("data:")) continue;
            String payload = line.substring(5).trim();
            if (payload.isBlank() || "[DONE]".equals(payload)) continue;
            try {
                JsonNode node = mapper.readTree(payload);
                if (node.path("id").asLong(-1) == id) return node;
                last = node;
            } catch (Exception ignored) { }
        }
        return last;
    }

    private void applyAuth(HttpRequest.Builder builder) {
        String token = server.authToken();
        if (token == null || token.isBlank()) return;
        if ("api-key".equalsIgnoreCase(server.authType())) builder.header("X-API-Key", token);
        else builder.header("Authorization", "Bearer " + token);
    }

    @Override public void close() { }
}
