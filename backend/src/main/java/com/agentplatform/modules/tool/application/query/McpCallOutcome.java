package com.agentplatform.modules.tool.application.query;

/**
 * MCP 工具调用结果（跨模块可见的只读视图）。
 *
 * <p>属于应用层查询模型，由 MCP 运行时服务在完成一次 {@code tools/call} 后返回，
 * 供 REST 接口或 Agent 工具调用循环消费。
 *
 * @param error 调用是否失败；工具自身返回 {@code isError=true} 或传输异常都会置为 {@code true}
 * @param text  面向调用方的文本结果：成功时为工具输出文本，失败时为错误信息
 */
public record McpCallOutcome(boolean error, String text) {
}
