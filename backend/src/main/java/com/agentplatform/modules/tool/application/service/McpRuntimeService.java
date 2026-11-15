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

/**
 * MCP 运行时服务（application 层）。
 *
 * <p>面向 Agent 运行时提供两项能力：把选中的 MCP 工具键转换为可下发给模型的
 * 工具绑定；执行 {@code tools/call} 并落审计记录。关键约束：
 * <ul>
 *   <li>工具或所属服务任一停用即快速失败，不发起网络/进程调用；</li>
 *   <li>每次调用无论成功失败都会写入审计记录，审计写入失败不影响调用结果；</li>
 *   <li>模型工具名需清洗并去重，避免不符合模型命名约束或彼此冲突。</li>
 * </ul>
 */
@Service
public class McpRuntimeService {
    /** MCP 服务仓储。 */
    private final McpServerRepository servers;
    /** MCP 工具仓储。 */
    private final McpToolRepository tools;
    /** 调用审计仓储。 */
    private final McpToolCallRepository calls;
    /** 按传输方式创建 MCP 会话的工厂。 */
    private final McpClientFactory factory;
    /** JSON 序列化器，用于审计记录与工具绑定。 */
    private final ObjectMapper mapper;

    /**
     * 构造运行时服务。
     *
     * @param servers MCP 服务仓储
     * @param tools   MCP 工具仓储
     * @param calls   调用审计仓储
     * @param factory MCP 客户端工厂
     * @param mapper  JSON 序列化器
     */
    public McpRuntimeService(McpServerRepository servers, McpToolRepository tools,
                             McpToolCallRepository calls, McpClientFactory factory, ObjectMapper mapper) {
        this.servers=servers; this.tools=tools; this.calls=calls; this.factory=factory; this.mapper=mapper;
    }

    /**
     * 将工具键解析为模型可用的工具绑定，仅包含已启用工具。
     *
     * <p>同一批绑定中出现重名时，对模型名称追加 {@code _2}、{@code _3} 等后缀；
     * 截断到 56 字符是为后缀与分隔符预留长度，避免超过 64 字符上限。
     *
     * @param keys 平台工具键列表
     * @return 工具绑定列表；不存在或已停用的键会被静默跳过
     */
    public List<McpToolBinding> bindings(List<String> keys) {
        List<McpToolBinding> result = new ArrayList<>();
        Map<String, String> used = new HashMap<>();
        for (McpTool tool : tools.findByKeys(keys)) {
            if (!tool.enabled()) continue;
            String modelName = modelName(tool.toolKey());
            String base = modelName;
            int i = 2;
            // 模型工具名在同一请求内必须唯一：冲突时追加序号后缀。
            while (used.containsValue(modelName)) modelName = base.substring(0, Math.min(base.length(), 56)) + "_" + i++;
            used.put(tool.toolKey(), modelName);
            result.add(new McpToolBinding(tool.toolKey(), modelName, tool.name(), tool.description(), tool.inputSchema()));
        }
        return result;
    }

    /**
     * 执行一次 MCP 工具调用，并记录审计日志。
     *
     * <p>失败语义：工具返回 {@code isError=true} 时结果为 {@code error=true} 但状态为
     * {@code ERROR}；连接/传输异常时状态为 {@code FAILED}。两种情况均返回
     * {@link McpCallOutcome} 而不抛异常，由调用方决定如何反馈给模型。
     *
     * @param toolKey   平台工具键
     * @param arguments 工具入参；{@code null} 视为空参数
     * @return 调用结果（错误标记 + 文本）
     * @throws IllegalArgumentException 工具或所属服务不存在
     * @throws IllegalStateException    工具或所属服务已停用
     */
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
                // 审计写入失败不能覆盖本次调用结果，因此单独吞掉异常。
                calls.save(new McpToolCall("call_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                        server.id(), toolKey, tool.name(), mapper.writeValueAsString(arguments), response,
                        status, elapsed, Instant.now()));
            } catch (Exception ignored) { }
        }
        return new McpCallOutcome(error, text);
    }

    /**
     * 将平台工具键转换为符合模型约束的工具名。
     *
     * <p>仅保留字母、数字、下划线、连字符；超长时截断并追加哈希后缀，兼顾可读性与唯一性。
     *
     * @param key 平台工具键
     * @return 清洗后的模型工具名，最长 64 字符
     */
    private String modelName(String key) {
        String sanitized = key.replaceAll("[^a-zA-Z0-9_-]", "_");
        if (sanitized.length() <= 64) return sanitized;
        return sanitized.substring(0, 55) + "_" + Math.abs(key.hashCode());
    }
}
