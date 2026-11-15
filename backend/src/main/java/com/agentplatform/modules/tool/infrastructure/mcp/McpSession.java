package com.agentplatform.modules.tool.infrastructure.mcp;

import java.util.List;
import java.util.Map;

/**
 * MCP 会话抽象（infrastructure 层）。
 *
 * <p>屏蔽 Streamable HTTP、SSE、stdio 三种传输差异，向上提供工具发现与工具调用
 * 两个协议动作。会话实现完成 {@code initialize} 握手后才可调用业务方法，
 * 且必须通过 {@link #close()} 释放连接/进程资源（配合 try-with-resources 使用）。
 */
public interface McpSession extends AutoCloseable {
    /**
     * 发现服务端工具列表（JSON-RPC {@code tools/list}）。
     *
     * @return 工具定义列表；服务端未返回数组时为空列表
     * @throws McpException 请求失败、超时或响应错误
     */
    List<McpToolDefinition> listTools();

    /**
     * 调用服务端工具（JSON-RPC {@code tools/call}）。
     *
     * @param name      服务端工具名
     * @param arguments 工具入参；{@code null} 视为空参数
     * @return 调用结果（错误标记、文本、原始 JSON）
     * @throws McpException 传输失败、超时或 JSON-RPC error
     */
    McpCallResult callTool(String name, Map<String, Object> arguments);

    /**
     * 关闭会话并释放底层连接、SSE 读取线程或子进程。
     */
    @Override void close();
}
