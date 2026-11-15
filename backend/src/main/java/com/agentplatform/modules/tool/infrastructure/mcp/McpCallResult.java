package com.agentplatform.modules.tool.infrastructure.mcp;

/**
 * MCP 工具调用的传输层结果（infrastructure 层）。
 *
 * <p>由各传输实现（Streamable HTTP / SSE / stdio）在完成 {@code tools/call} 后返回，
 * 上层据此生成审计记录与 {@code McpCallOutcome}。
 *
 * @param error   工具是否返回错误（对应 MCP 结果中的 {@code isError}，或 JSON-RPC error）
 * @param text    从 {@code result.content} 中提取的文本，便于直接展示或回填给模型
 * @param rawJson 原始 JSON-RPC 响应，用于审计归档
 */
public record McpCallResult(boolean error, String text, String rawJson) {
}
