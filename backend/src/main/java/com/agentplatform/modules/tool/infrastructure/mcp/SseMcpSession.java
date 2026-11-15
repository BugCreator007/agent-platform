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

/**
 * 基于旧版 HTTP+SSE 传输的 MCP 会话实现（infrastructure 层）。
 *
 * <p>协议流程：先建立长期 SSE 连接，等待服务端下发 {@code endpoint} 事件拿到消息投递
 * 地址；随后通过该地址 POST JSON-RPC 请求（{@code initialize}、{@code tools/list}、
 * {@code tools/call}），响应则从 SSE 流中按 JSON-RPC {@code id} 匹配回填。
 *
 * <p>关键约束：
 * <ul>
 *   <li>{@code endpoint} 事件最多等待 20 秒，否则握手失败；</li>
 *   <li>每个请求最多等待 45 秒，超时抛 {@link McpException}；</li>
 *   <li>SSE 读取在虚拟线程中进行，关闭会话时需同时关闭流并中断线程。</li>
 * </ul>
 */
public class SseMcpSession implements McpSession {
    /** 服务配置（地址、鉴权令牌等）。 */
    private final McpServer server;
    /** JSON 序列化器。 */
    private final ObjectMapper mapper;
    /** 原生 HTTP 客户端，连接超时 10 秒。 */
    private final HttpClient client;
    /** JSON-RPC 请求 ID 生成器，从 1 开始单调递增。 */
    private final AtomicLong ids = new AtomicLong(1);
    /** 等待 SSE {@code endpoint} 事件的同步闩。 */
    private final CountDownLatch endpointReady = new CountDownLatch(1);
    /** 已发出但未收到响应的请求：ID → 等待中的 Future。 */
    private final ConcurrentHashMap<Long, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
    /** 服务端通过 SSE 下发的消息投递地址。 */
    private volatile String messageEndpoint;
    /** SSE 响应行流。 */
    private volatile Stream<String> eventStream;
    /** 执行 SSE 读取循环的虚拟线程。 */
    private volatile Thread reader;

    /**
     * 构造 SSE 会话（尚未连接，需调用 {@link #open()}）。
     *
     * @param server MCP 服务配置
     * @param mapper JSON 序列化器
     */
    public SseMcpSession(McpServer server, ObjectMapper mapper) {
        this.server = server;
        this.mapper = mapper;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    /**
     * 建立 SSE 连接、等待 endpoint 事件并完成 MCP 初始化握手。
     *
     * @return 当前会话（已初始化）
     * @throws McpException 服务停用、URL 为空、连接失败、endpoint 超时或 initialize 失败
     */
    public McpSession open() {
        if (!server.enabled()) throw new McpException("MCP 服务已停用");
        if (server.url() == null || server.url().isBlank()) throw new McpException("SSE URL 不能为空");
        startEventStream();
        try {
            // 必须先拿到服务端下发的 message endpoint，才能发送任何 JSON-RPC 请求。
            if (!endpointReady.await(20, TimeUnit.SECONDS)) throw new McpException("SSE endpoint 事件超时");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new McpException("SSE 连接被中断", e);
        }
        request("initialize", Map.of("protocolVersion", "2025-03-26", "capabilities", Map.of(),
                "clientInfo", Map.of("name", "agent-platform", "version", "1.0")));
        // initialized 是通知（无 id、无响应），失败也不阻塞握手。
        notify("notifications/initialized", Map.of());
        return this;
    }

    /**
     * 发起 SSE GET 请求并在虚拟线程中启动读取循环。
     *
     * @throws McpException 连接失败或返回非 2xx
     */
    private void startEventStream() {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(server.url()))
                    // SSE 为长连接，读超时设置得较长（300 秒），由上层调用超时兜底。
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

    /**
     * SSE 事件解析循环：按空行分隔事件，累积 {@code data:} 行并识别 {@code event:} 类型。
     *
     * <p>SSE 规范允许一个事件包含多行 data；此处用换行拼接后交给 {@link #dispatch}。
     * 流关闭属于正常终止，异常被静默忽略。
     */
    private void readLoop() {
        String event = "message";
        StringBuilder data = new StringBuilder();
        try {
            for (var it = eventStream.iterator(); it.hasNext(); ) {
                String line = it.next();
                if (line.isEmpty()) {
                    // 空行表示一个事件结束，分发后重置解析状态。
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

    /**
     * 分发一个完整的 SSE 事件。
     *
     * <p>{@code endpoint} 事件用于确定消息投递地址（相对地址按服务 URL 解析）；
     * 其他事件按 JSON-RPC 响应处理，按 {@code id} 唤醒对应的等待 Future。
     *
     * @param event 事件类型
     * @param data  事件数据
     */
    private void dispatch(String event, String data) {
        if (data.isBlank()) return;
        if ("endpoint".equals(event)) {
            // endpoint 可能是相对路径，需相对原始服务 URL 解析为绝对地址。
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

    /**
     * 发送 JSON-RPC 请求并等待匹配 ID 的响应。
     *
     * <p>请求 ID 与 Future 先登记再 POST，避免响应先于登记到达造成丢失；
     * 无论成功失败都在 finally 中移除登记。
     *
     * @param method JSON-RPC 方法名
     * @param params 方法参数
     * @return 完整 JSON-RPC 响应节点（含 {@code result}）
     * @throws McpException 投递失败、45 秒内未收到响应，或响应包含 {@code error}
     */
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

    /**
     * 发送 JSON-RPC 通知（无 id、不等待响应）。
     *
     * @param method 方法名
     * @param params 参数
     */
    private void notify(String method, Object params) {
        try {
            post(Map.of("jsonrpc", "2.0", "method", method, "params", params));
        } catch (Exception ignored) { }
    }

    /**
     * 向 SSE 下发的事件端点 POST JSON 消息。
     *
     * @param body 已构造的 JSON-RPC 消息
     * @throws Exception 端点未就绪、序列化失败、网络错误或非 2xx 响应
     */
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

    /**
     * 发现服务端工具列表（{@code tools/list}）。
     *
     * <p>缺失 {@code inputSchema} 时回退为 {@code {"type":"object"}}，保证下游模型
     * 始终能拿到合法 schema。
     *
     * @return 工具定义列表
     * @throws McpException 请求失败或超时
     */
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

    /**
     * 调用服务端工具（{@code tools/call}）。
     *
     * <p>把 {@code result.content} 数组中各项的 {@code text} 拼接为纯文本；
     * 非文本内容回退为该项 JSON 字符串。
     *
     * @param name      服务端工具名
     * @param arguments 工具入参
     * @return 调用结果
     * @throws McpException 请求失败或超时
     */
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
     * 关闭 SSE 流并中断读取线程，释放连接资源。
     */
    @Override
    public void close() {
        try { if (eventStream != null) eventStream.close(); } catch (Exception ignored) { }
        try { if (reader != null) reader.interrupt(); } catch (Exception ignored) { }
    }
}
