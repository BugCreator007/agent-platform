package com.agentplatform.modules.chat.infrastructure.ai;

import java.util.List;

public record AiMessage(String role, String content, List<ToolCall> toolCalls, String toolCallId) {
    public AiMessage(String role, String content) {
        this(role, content, List.of(), null);
    }
}
