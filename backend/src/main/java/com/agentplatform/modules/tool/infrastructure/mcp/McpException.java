package com.agentplatform.modules.tool.infrastructure.mcp;

/**
 * MCP 客户端异常（infrastructure 层）。
 *
 * <p>用于统一包装协议握手失败、HTTP 非 2xx、JSON-RPC error、超时、stdio 启动失败等
 * 传输层错误；上层应用服务捕获后转换为业务异常或调用失败结果。
 */
public class McpException extends RuntimeException {
    /**
     * 使用错误信息构造异常。
     *
     * @param message 错误描述
     */
    public McpException(String message) { super(message); }

    /**
     * 使用错误信息与根因构造异常。
     *
     * @param message 错误描述
     * @param cause   根因异常
     */
    public McpException(String message, Throwable cause) { super(message, cause); }
}
