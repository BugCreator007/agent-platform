package com.agentplatform.modules.tool.application.query;

import java.time.Instant;

/**
 * MCP 服务管理视图（application 层查询模型，跨模块可见的只读投影）。
 *
 * <p>由 {@code McpServerApplicationService} 从领域对象转换而来，用于 REST 返回；
 * 刻意不包含 {@code authToken}，避免密钥泄漏到接口响应。
 *
 * @param id              服务主键，形如 {@code mcp_xxxxxxxxxx}
 * @param name            服务显示名称
 * @param transport       传输方式：{@code streamable-http}、{@code sse} 或 {@code stdio}
 * @param url             HTTP/SSE 传输的访问地址；stdio 传输通常为空
 * @param command         stdio 传输的启动命令；HTTP/SSE 传输通常为空
 * @param args            stdio 启动参数（已按空格/引号切分前的原始字符串）
 * @param authType        鉴权类型：{@code bearer} 或 {@code api-key}
 * @param enabled         服务是否启用
 * @param status          连接状态：如 {@code CONNECTED}、{@code DISCONNECTED}、{@code ERROR}
 * @param lastError       最近一次连接失败的错误信息，成功后清空
 * @param lastConnectedAt 最近一次成功连接的时间
 * @param toolCount       该服务下已发现的工具数量
 */
public record McpServerView(String id, String name, String transport, String url, String command,
                            String args, String authType, boolean enabled, String status,
                            String lastError, Instant lastConnectedAt, long toolCount) {
}
