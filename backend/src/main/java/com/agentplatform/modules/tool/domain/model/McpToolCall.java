package com.agentplatform.modules.tool.domain.model;

import java.time.Instant;

/**
 * MCP 工具调用审计记录（领域值对象）。
 *
 * <p>所在层：domain。每次 {@code tools/call} 都会生成一条记录，用于管理端追踪
 * 调用参数、响应与耗时。注意：{@code requestJson}/{@code responseJson} 可能包含
 * 业务数据，展示时需按审计要求脱敏，禁止在其中写入密钥或完整提示词。
 *
 * @param id           调用记录主键，形如 {@code call_xxxxxxxxxxxx}
 * @param serverId     所属 MCP 服务 ID
 * @param toolKey      平台工具键
 * @param toolName     调用时传给 MCP 服务端的工具名
 * @param requestJson  调用参数序列化后的 JSON
 * @param responseJson 工具返回的原始 JSON；传输失败时可能为 {@code null}
 * @param status       调用状态：{@code SUCCESS}、{@code ERROR}（工具返回错误）或
 *                     {@code FAILED}（连接/传输异常）
 * @param durationMs   从开始调用到记录完成的耗时（毫秒）
 * @param createdAt    调用发生时间
 */
public record McpToolCall(String id, String serverId, String toolKey, String toolName,
                          String requestJson, String responseJson, String status,
                          long durationMs, Instant createdAt) {
}
