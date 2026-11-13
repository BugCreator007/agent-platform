package com.agentplatform.modules.tool.infrastructure.mcp;

import com.agentplatform.modules.tool.domain.model.McpServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

public class SseMcpSession implements McpSession {
    private final McpServer server;
    private final ObjectMapper mapper;
    private final HttpClient client;
    private final AtomicLong ids = new AtomicLong(1);
    private final CountDownLatch endpointReady = new CountDownLatch(1);
    private final ConcurrentHashMap<Long, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
    private volatile String messageEndpoint;
    private volatile Stream<String> eventStream;
    private volatile Thread reader;

    public SseMcpSession(McpServer server, ObjectMapper mapper) {
        this.server = server;
        this.mapper = mapper;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public McpSession open() {
        if (!server.enabled()) throw new McpException("MCP 服务已停用");
        if (server.url() == null || server.url().isBlank()) throw new McpException("SSE URL 不能为空");
        startEventStream();
        try {
            if (!endpointReady.await(20, TimeUnit.SECONDS)) throw new McpException("SSE endpoint 事件超时");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new McpException("SSE 连接被中断", e);
        }
        request("initialize", Map.of("protocolVersion", "2025-03-26", "capabilities", Map.of(),
                "clientInfo", Map.of("name", "agent-platform", "version", "1.0")));
        notify("notifications/initialized", Map.of());
        return this;
    }

    private void startEventStream() {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(server.url()))
                    .timeout(Duration.ofSeconds(300))
                    .header("Accept", "text/event-stream")
                    .GET();
            applyAuth(builder);
            HttpResponse<Stream<String>> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() / 100 != 2) throw new McpException("SSE HTTP " + response.statusCode());
            eventStream = response.body();
            reader = Thread.ofVirtual().name("mcp-sse-" + server.id()).start(this::readLoop);
        } catch (McpException e) {
            throw e;
        } catch (Exception e) {
            throw new McpException("SSE 连接失败: " + e.getMessage(), e);
        }
    }

    private void readLoop() {
        String event = "message";
        StringBuilder data = new StringBuilder();
        try {
            for (var it = eventStream.iterator(); it.hasNext(); ) {
                String line = it.next();
                if (line.isEmpty()) {
                    dispatch(event, data.toString());
                    event = "message";
                    data.setLength(0);
                } else if (line.startsWith("event:")) {
                    event = line.substring(6).trim();
                } else if (line.startsWith("data:")) {
                    if (data.length() > 0) data.append('\n');
                    data.append(line.substring(5).trim());
                }
            }
        } catch (Exception ignored) {
            // stream closed
        }
    }

    private void dispatch(String event, String data) {
        if (data.isBlank()) return;
        if ("endpoint".equals(event)) {
            messageEndpoint = URI.create(server.url()).resolve(data.trim()).toString();
            endpointReady.countDown();
            return;
        }
        try {
            JsonNode node = mapper.readTree(data);
            long id = node.path("id").asLong(-1);
            CompletableFuture<JsonNode> future = pending.get(id);
            if (future != null) future.complete(node);
        } catch (Exception ignored) { }
    }

    private JsonNode request(String method, Object params) {
        long id = ids.getAndIncrement();
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        pending.put(id, future);
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("jsonrpc", "2.0");
            body.put("id", id);
            body.put("method", method);
            body.put("params", params);
            post(body);
            JsonNode response = future.get(45, TimeUnit.SECONDS);
            JsonNode error = response.path("error");
            if (!error.isMissingNode()) throw new McpException("MCP " + method + " 失败: " + error.path("message").asText(error.toString()));
            return response;
        } catch (McpException e) {
            throw e;
        } catch (Exception e) {
            throw new McpException("MCP SSE 请求失败: " + e.getMessage(), e);
        } finally {
            pending.remove(id);
        }
    }

    private void notify(String method, Object params) {
        try {
            post(Map.of("jsonrpc", "2.0", "method", method, "params", params));
        } catch (Exception ignored) { }
    }

    private void post(Object body) throws Exception {
        if (messageEndpoint == null) throw new McpException("SSE message endpoint 未就绪");
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(messageEndpoint))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
        applyAuth(builder);
        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) throw new McpException("MCP SSE POST " + response.statusCode());
    }

    @Override
    public List<McpToolDefinition> listTools() {
        JsonNode response = request("tools/list", Map.of());
        List<McpToolDefinition> definitions = new ArrayList<>();
        JsonNode tools = response.path("result").path("tools");
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
        JsonNode result = response.path("result");
        StringBuilder text = new StringBuilder();
        JsonNode content = result.path("content");
        if (content.isArray()) {
            for (JsonNode item : content) text.append(item.path("text").asText(item.toString())).append('\n');
        }
        return new McpCallResult(result.path("isError").asBoolean(false), text.toString().trim(), response.toString());
    }

    private void applyAuth(HttpRequest.Builder builder) {
        String token = server.authToken();
        if (token == null || token.isBlank()) return;
        if ("api-key".equalsIgnoreCase(server.authType())) builder.header("X-API-Key", token);
        else builder.header("Authorization", "Bearer " + token);
    }

    @Override
    public void close() {
        try { if (eventStream != null) eventStream.close(); } catch (Exception ignored) { }
        try { if (reader != null) reader.interrupt(); } catch (Exception ignored) { }
    }
}
