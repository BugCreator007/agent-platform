package com.agentplatform.modules.chat.application.runtime;

/**
 * 流式对话请求体（application 层入参 DTO）。
 *
 * @param agentId   目标智能体 ID，必填
 * @param message   本轮用户消息
 * @param sessionId 续聊的会话 ID；为空时由运行时自动生成新会话
 */
public record RuntimeChatRequest(String agentId, String message, String sessionId) {
}
