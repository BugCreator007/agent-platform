package com.agentplatform.modules.chat.infrastructure.ai;

import java.util.List;

public record ChatChunk(String content, boolean done, List<ToolCallDelta> toolCalls) {
    public static ChatChunk text(String content) { return new ChatChunk(content, false, List.of()); }
    public static ChatChunk finish() { return new ChatChunk("", true, List.of()); }
}
