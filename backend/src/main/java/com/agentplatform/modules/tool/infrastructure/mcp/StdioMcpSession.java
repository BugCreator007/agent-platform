package com.agentplatform.modules.tool.infrastructure.mcp;

import com.agentplatform.modules.tool.domain.model.McpServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于 stdio 传输的 MCP 会话实现（infrastructure 层）。
 *
 * <p>启动本地子进程，通过 stdin/stdout 以“一行一个 JSON-RPC 消息”的方式通信，
 * stderr 直接丢弃。关键约束：
 * <ul>
 *   <li>可执行文件必须命中命令白名单（按文件名或完整命令匹配），防止任意命令执行；</li>
 *   <li>每个请求最多等待 45 秒，超时抛 {@link McpException}；</li>
 *   <li>关闭会话时关闭 stdin、销毁子进程并中断读取线程。</li>
 * </ul>
 */
public class StdioMcpSession implements McpSession {
    /** 服务配置（命令、参数等）。 */
    private final McpServer server;
    /** JSON 序列化器。 */
    private final ObjectMapper mapper;
    /** 允许启动的命令白名单。 */
    private final Set<String> allowedCommands;
    /** JSON-RPC 请求 ID 生成器。 */
    private final AtomicLong ids = new AtomicLong(1);
    /** 已发出但未收到响应的请求：ID → 等待中的 Future。 */
    private final ConcurrentHashMap<Long, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
    /** MCP 服务端子进程。 */
    private Process process;
    /** 子进程 stdin 写入器。 */
    private BufferedWriter stdin;
    /** 读取子进程 stdout 的虚拟线程。 */
    private Thread reader;

    /**
     * 构造 stdio 会话（尚未启动子进程，需调用 {@link #open()}）。
     *
     * @param server          MCP 服务配置
     * @param mapper          JSON 序列化器
     * @param allowedCommands 允许启动的命令白名单
     */
    public StdioMcpSession(McpServer server, ObjectMapper mapper, Set<String> allowedCommands) {
        this.server=server; this.mapper=mapper; this.allowedCommands=allowedCommands;
    }

    /**
     * 校验命令白名单、启动子进程并完成 MCP 初始化握手。
     *
     * @return 当前会话（已初始化）
     * @throws McpException 服务停用、命令为空、命令不在白名单、进程启动失败或 initialize 失败
     */
    public McpSession open() {
        if (!server.enabled()) throw new McpException("MCP 服务已停用");
        if (server.command() == null || server.command().isBlank()) throw new McpException("stdio 启动命令不能为空");
        String base = java.nio.file.Path.of(server.command()).getFileName().toString();
        // 同时接受纯命令名（npx）与带路径的完整命令（/usr/bin/npx 取文件名后匹配）。
        if (!allowedCommands.contains(base) && !allowedCommands.contains(server.command())) {
            throw new McpException("stdio 命令未在允许列表中: " + server.command());
        }
        try {
            List<String> command = new ArrayList<>();
            command.add(server.command());
            command.addAll(splitArgs(server.args()));
            ProcessBuilder builder = new ProcessBuilder(command);
            // stderr 可能包含噪音或敏感信息，直接丢弃，避免污染应用日志。
            builder.redirectError(ProcessBuilder.Redirect.DISCARD);
            process = builder.start();
            stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader stdout = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            reader = Thread.ofVirtual().name("mcp-stdio-" + server.id()).start(() -> readLoop(stdout));
        } catch (Exception e) {
            throw new McpException("stdio 启动失败: " + e.getMessage(), e);
        }
        request("initialize", Map.of("protocolVersion", "2025-03-26", "capabilities", Map.of(),
                "clientInfo", Map.of("name", "agent-platform", "version", "1.0")));
        // initialized 是通知，不等待响应。
        notify("notifications/initialized", Map.of());
        return this;
    }

    /**
     * 读取子进程 stdout：逐行解析 JSON-RPC 响应，按 {@code id} 唤醒等待的 Future。
     *
     * <p>只处理以 {@code {} 开头的行，其他输出（日志等）被忽略。
     *
     * @param stdout 子进程标准输出读取器
     */
    private void readLoop(BufferedReader stdout) {
        try {
            String line;
            while ((line = stdout.readLine()) != null) {
                line = line.trim();
                if (!line.startsWith("{")) continue;
                try {
                    JsonNode node = mapper.readTree(line);
                    long id = node.path("id").asLong(-1);
                    CompletableFuture<JsonNode> future = pending.get(id);
                    if (future != null) future.complete(node);
                } catch (Exception ignored) { }
            }
        } catch (Exception ignored) { }
    }

    /**
     * 发送 JSON-RPC 请求并等待匹配 ID 的响应。
     *
     * @param method JSON-RPC 方法名
     * @param params 方法参数
     * @return 完整 JSON-RPC 响应节点
     * @throws McpException 写入失败、45 秒内未收到响应，或响应包含 {@code error}
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
            send(body);
            JsonNode response = future.get(45, TimeUnit.SECONDS);
            JsonNode error = response.path("error");
            if (!error.isMissingNode()) throw new McpException("MCP " + method + " 失败: " + error.path("message").asText(error.toString()));
            return response;
        } catch (McpException e) {
            throw e;
        } catch (Exception e) {
            throw new McpException("MCP stdio 请求失败: " + e.getMessage(), e);
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
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("jsonrpc", "2.0");
            body.put("method", method);
            body.put("params", params);
            send(body);
        } catch (Exception ignored) { }
    }

    /**
     * 以单行 JSON 写入子进程 stdin 并刷新。
     *
     * @param body JSON-RPC 消息
     * @throws Exception 序列化或写入失败
     */
    private void send(Object body) throws Exception {
        stdin.write(mapper.writeValueAsString(body));
        stdin.newLine();
        stdin.flush();
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
        List<McpToolDefinition> definitions = new ArrayList<>();
        JsonNode tools = response.path("result").path("tools");
        if (tools.isArray()) {
            for (JsonNode tool : tools) {
                definitions.add(new McpToolDefinition(tool.path("name").asText(), tool.path("description").asText(""),
                        tool.path("inputSchema").isMissingNode() ? "{\"type\":\"object\"}" : tool.path("inputSchema").toString()));
            }
        }
        return definitions;
    }

    /**
     * 调用服务端工具（{@code tools/call}）。
     *
     * @param name      服务端工具名
     * @param arguments 工具入参
     * @return 调用结果（拼接后的文本 + 原始 JSON）
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
     * 切分 stdio 启动参数：支持双引号包裹的空格参数，其余按空白切分。
     *
     * @param args 原始参数字符串，可为 {@code null}
     * @return 参数列表；入参为空时返回空列表
     */
    private List<String> splitArgs(String args) {
        List<String> result = new ArrayList<>();
        if (args == null || args.isBlank()) return result;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"([^\"]*)\"|(\\S+)").matcher(args);
        while (m.find()) result.add(m.group(1) != null ? m.group(1) : m.group(2));
        return result;
    }

    /**
     * 关闭 stdin、销毁子进程并中断读取线程。
     */
    @Override
    public void close() {
        try { if (stdin != null) stdin.close(); } catch (Exception ignored) { }
        try { if (process != null) process.destroy(); } catch (Exception ignored) { }
        try { if (reader != null) reader.interrupt(); } catch (Exception ignored) { }
    }
}
