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

/**
 * 基于 MCP Streamable HTTP 传输的会话实现（infrastructure 层）。
 *
 * <p>每个 JSON-RPC 请求通过独立 POST 发送；服务端可返回 {@code application/json}
 * 或 {@code text/event-stream}，两种响应都由本类解析。握手成功后服务端可能通过
 * {@code Mcp-Session-Id} 响应头分配会话 ID，后续请求需回传该头。
 *
 * <p>关键约束：请求超时 45 秒；JSON-RPC {@code error} 统一转换为
 * {@link McpException}；通知失败只记 debug 日志，不影响主流程。
 */
public class StreamableHttpMcpSession implements McpSession {
    /** 日志记录器。 */
    private static final Logger log = LoggerFactory.getLogger(StreamableHttpMcpSession.class);
    /** 服务配置（地址、鉴权令牌等）。 */
    private final McpServer server;
    /** JSON 序列化器。 */
    private final ObjectMapper mapper;
    /** 原生 HTTP 客户端，连接超时 10 秒。 */
    private final HttpClient client;
    /** JSON-RPC 请求 ID 生成器。 */
    private final AtomicLong ids = new AtomicLong(1);
    /** 服务端分配的 MCP 会话 ID（来自 {@code Mcp-Session-Id} 响应头）。 */
    private String sessionId;

    /**
     * 构造 Streamable HTTP 会话（尚未握手，需调用 {@link #open()}）。
     *
     * @param server MCP 服务配置
     * @param mapper JSON 序列化器
     */
    public StreamableHttpMcpSession(McpServer server, ObjectMapper mapper) {
        this.server = server;
        this.mapper = mapper;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    /**
     * 完成 MCP 初始化握手并发送 initialized 通知。
     *
     * @return 当前会话（已初始化）
     * @throws McpException 服务停用、initialize 失败或响应为空
     */
    public McpSession open() {
        if (!server.enabled()) throw new McpException("MCP 服务已停用");
        JsonNode result = request("initialize", Map.of(
                "protocolVersion", "2025-03-26",
                "capabilities", Map.of(),
                "clientInfo", Map.of("name", "agent-platform", "version", "1.0")));
        // request 内部已对 JSON-RPC error 抛异常；这里再做一次防御性检查，防止空响应。
        if (result == null || result.path("error").isMissingNode() == false) {
            throw new McpException("MCP initialize 失败: " + (result == null ? "empty response" : result.path("error").toString()));
        }
        notify("notifications/initialized", Map.of());
        return this;
    }

    /**
     * 发现服务端工具列表（{@code tools/list}）。
     *
     * @return 工具定义列表；缺失 schema 时回退为 {@code {"type":"object"}}
     * @throws McpException 请求失败或超时
     */
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

    /**
     * 调用服务端工具（{@code tools/call}）。
     *
     * <p>JSON-RPC 层错误转换为 {@code error=true} 的结果而不抛异常，便于上层把错误
     * 文本回填给模型；结果内容兼容文本项、非文本项与纯字符串三种形态。
     *
     * @param name      服务端工具名
     * @param arguments 工具入参
     * @return 调用结果
     * @throws McpException 传输失败或超时
     */
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
                    // 图片、资源等非文本内容以 JSON 形式保留，避免信息丢失。
                    text.append(item.toString()).append('\n');
                }
            }
        } else if (result.isTextual()) {
            text.append(result.asText());
        }
        boolean isError = result.path("isError").asBoolean(false);
        return new McpCallResult(isError, text.toString().trim(), response.toString());
    }

    /**
     * 发送 JSON-RPC 请求并解析响应（JSON 或 SSE）。
     *
     * @param method JSON-RPC 方法名
     * @param params 方法参数
     * @return 完整 JSON-RPC 响应节点
     * @throws McpException 非 2xx、响应为空、JSON-RPC error、网络错误或超时
     */
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
                    // Streamable HTTP 允许服务端以 SSE 或 JSON 响应，两者都声明在 Accept 中。
                    .header("Accept", "application/json, text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            applyAuth(builder);
            if (sessionId != null && !sessionId.isBlank()) builder.header("Mcp-Session-Id", sessionId);
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            // 服务端可能在任意响应中首次下发会话 ID，记录后供后续请求回传。
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

    /**
     * 发送 JSON-RPC 通知（无 id、不等待业务响应）。
     *
     * <p>通知失败（包括网络异常）只记 debug 日志，不中断调用方流程。
     *
     * @param method 方法名
     * @param params 参数
     */
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

    /**
     * 解析内联在响应体中的 SSE 事件，返回匹配请求 ID 的 JSON-RPC 消息。
     *
     * <p>逐行收集 {@code data:} 负载（跳过空行与 {@code [DONE]}）；优先返回
     * {@code id} 匹配的消息，未匹配时退化为最后一条可解析消息。
     *
     * @param body 响应体
     * @param id   当前请求 ID
     * @return 解析出的 JSON 节点；响应为空或无可解析消息时返回 {@code null}
     * @throws Exception JSON 解析异常（由调用方统一包装）
     */
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

    /**
     * 按配置附加鉴权头：{@code api-key} 使用 {@code X-API-Key}，其余使用 Bearer Token。
     *
     * @param builder 待发送的请求构建器
     */
    private void applyAuth(HttpRequest.Builder builder) {
        String token = server.authToken();
        if (token == null || token.isBlank()) return;
        if ("api-key".equalsIgnoreCase(server.authType())) builder.header("X-API-Key", token);
        else builder.header("Authorization", "Bearer " + token);
    }

    /**
     * 关闭会话。Streamable HTTP 为无状态短连接，无需释放额外资源。
     */
    @Override public void close() { }
}
