package com.agentplatform.modules.chat.infrastructure.ai;

public record ToolCallDelta(int index, String id, String name, String arguments) {
}
