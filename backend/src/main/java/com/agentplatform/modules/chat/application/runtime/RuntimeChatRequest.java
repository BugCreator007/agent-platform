package com.agentplatform.modules.chat.application.runtime;

public record RuntimeChatRequest(String agentId, String message, String sessionId) {
}
