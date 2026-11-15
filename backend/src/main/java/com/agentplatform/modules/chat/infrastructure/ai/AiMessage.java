package com.agentplatform.modules.chat.infrastructure.ai;

import java.util.List;

/**
 * 发送给模型供应商的通用消息（infrastructure 层内部模型）。
 *
 * <p>在 OpenAI 与 Anthropic 两种协议间充当中间表示，由各客户端转换为供应商格式。</p>
 *
 * @param role       角色：system（仅提示词拼装时使用）、user、assistant、tool
 * @param content    消息文本；工具结果消息中为工具返回内容
 * @param toolCalls  assistant 消息发起的工具调用列表；普通消息为空列表
 * @param toolCallId tool 结果消息对应的工具调用 ID；其他消息为 null
 */
public record AiMessage(String role, String content, List<ToolCall> toolCalls, String toolCallId) {
    /**
     * 构造不含工具调用的普通文本消息。
     *
     * @param role    消息角色
     * @param content 消息文本
     */
    public AiMessage(String role, String content) {
        this(role, content, List.of(), null);
    }
}
