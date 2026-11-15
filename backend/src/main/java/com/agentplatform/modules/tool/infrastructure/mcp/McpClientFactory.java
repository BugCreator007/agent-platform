package com.agentplatform.modules.tool.infrastructure.mcp;

import com.agentplatform.modules.tool.domain.model.McpServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * MCP 会话工厂（infrastructure 层）。
 *
 * <p>按服务的传输方式创建并完成握手：{@code streamable-http}（含别名 {@code http}、
 * {@code streamable_http}）、{@code sse}、{@code stdio}。stdio 的可执行文件必须命中
 * 命令白名单，白名单通过 {@code app.mcp.stdio.allowed-commands} 配置，
 * 默认 {@code npx,node,python,python3,uvx}，用于降低任意命令执行风险。
 */
@Component
public class McpClientFactory {
    /** JSON 序列化器，注入各会话实现。 */
    private final ObjectMapper mapper;
    /** 允许用于 stdio 的启动命令白名单。 */
    private final Set<String> allowedCommands;

    /**
     * 构造工厂，并解析逗号分隔的命令白名单。
     *
     * @param mapper          JSON 序列化器
     * @param allowedCommands 逗号分隔的 stdio 命令白名单配置
     */
    public McpClientFactory(ObjectMapper mapper,
                            @Value("${app.mcp.stdio.allowed-commands:npx,node,python,python3,uvx}") String allowedCommands) {
        this.mapper = mapper;
        this.allowedCommands = Arrays.stream(allowedCommands.split(",")).map(String::trim)
                .filter(s -> !s.isBlank()).collect(Collectors.toSet());
    }

    /**
     * 按服务传输方式打开一个已完成初始化的 MCP 会话。
     *
     * <p>调用方必须负责关闭返回的会话。
     *
     * @param server MCP 服务配置
     * @return 已握手成功的会话
     * @throws McpException 传输方式不支持、连接失败、握手失败或 stdio 命令不在白名单
     */
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
