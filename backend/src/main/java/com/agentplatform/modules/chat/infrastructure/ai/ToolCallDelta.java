package com.agentplatform.modules.chat.infrastructure.ai;

/**
 * 流式响应中的工具调用增量（infrastructure 层内部模型）。
 *
 * <p>供应商会把一次工具调用的 ID、名称和参数 JSON 分多个片段下发，
 * 运行时按 {@code index} 聚合后才能得到完整参数。</p>
 *
 * @param index     同一轮响应内的工具调用序号，用于聚合分片
 * @param id        调用 ID；仅在首个分片中出现，后续分片可能为空
 * @param name      工具名；仅在首个分片中出现，后续分片可能为空
 * @param arguments 参数 JSON 的增量片段
 */
public record ToolCallDelta(int index, String id, String name, String arguments) {
}
