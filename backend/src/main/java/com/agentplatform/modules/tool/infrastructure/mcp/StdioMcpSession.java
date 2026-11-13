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

public class StdioMcpSession implements McpSession {
    private final McpServer server;
    private final ObjectMapper mapper;
    private final Set<String> allowedCommands;
    private final AtomicLong ids = new AtomicLong(1);
    private final ConcurrentHashMap<Long, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
    private Process process;
    private BufferedWriter stdin;
    private Thread reader;

    public StdioMcpSession(McpServer server, ObjectMapper mapper, Set<String> allowedCommands) {
        this.server=server; this.mapper=mapper; this.allowedCommands=allowedCommands;
    }

    public McpSession open() {
        if (!server.enabled()) throw new McpException("MCP 服务已停用");
        if (server.command() == null || server.command().isBlank()) throw new McpException("stdio 启动命令不能为空");
        String base = java.nio.file.Path.of(server.command()).getFileName().toString();
        if (!allowedCommands.contains(base) && !allowedCommands.contains(server.command())) {
            throw new McpException("stdio 命令未在允许列表中: " + server.command());
        }
        try {
            List<String> command = new ArrayList<>();
            command.add(server.command());
            command.addAll(splitArgs(server.args()));
            ProcessBuilder builder = new ProcessBuilder(command);
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
        notify("notifications/initialized", Map.of());
        return this;
    }

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

    private void notify(String method, Object params) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("jsonrpc", "2.0");
            body.put("method", method);
            body.put("params", params);
            send(body);
        } catch (Exception ignored) { }
    }

    private void send(Object body) throws Exception {
        stdin.write(mapper.writeValueAsString(body));
        stdin.newLine();
        stdin.flush();
    }

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

    private List<String> splitArgs(String args) {
        List<String> result = new ArrayList<>();
        if (args == null || args.isBlank()) return result;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"([^\"]*)\"|(\\S+)").matcher(args);
        while (m.find()) result.add(m.group(1) != null ? m.group(1) : m.group(2));
        return result;
    }

    @Override
    public void close() {
        try { if (stdin != null) stdin.close(); } catch (Exception ignored) { }
        try { if (process != null) process.destroy(); } catch (Exception ignored) { }
        try { if (reader != null) reader.interrupt(); } catch (Exception ignored) { }
    }
}
