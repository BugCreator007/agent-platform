package com.agentplatform.modules.chat.infrastructure.ai;

/**
 * 模型发起的一次完整工具调用（infrastructure 层内部模型）。
 *
 * @param id        供应商生成的调用 ID，用于回传工具结果时关联
 * @param name      工具名（模型侧名称）
 * @param arguments 工具参数 JSON 字符串
 */
public record ToolCall(String id, String name, String arguments) {
}
