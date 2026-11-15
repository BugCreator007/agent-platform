package com.agentplatform.modules.tool.interfaces.dto;

/**
 * MCP 服务创建/更新请求（interfaces 层 DTO）。
 *
 * <p>更新时字段为 {@code null} 表示保持原值。注意 {@code authToken} 属于敏感信息，
 * 禁止写入日志或审计明细。
 *
 * @param name      服务显示名称；创建时必填
 * @param transport 传输方式：{@code streamable-http}/{@code sse}/{@code stdio}；
 *                  创建时缺省为 {@code streamable-http}
 * @param url       HTTP/SSE 访问地址；HTTP/SSE 传输必填
 * @param command   stdio 启动命令；stdio 传输必填
 * @param args      stdio 启动参数原始字符串
 * @param authType  鉴权类型：{@code bearer}/{@code api-key}；创建时缺省 {@code bearer}
 * @param authToken 鉴权令牌/API Key（敏感）
 * @param enabled   是否启用；创建时缺省为 {@code true}
 */
public record McpServerRequest(String name, String transport, String url, String command, String args,
                               String authType, String authToken, Boolean enabled) {
}
