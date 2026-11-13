package com.agentplatform.modules.chat.domain.model;

import java.util.List;

public record ChatSession(
        String id, String agentId, String agentName, String status, int turns,
        int tools, int tokens, long ms, String time, String topic, List<TraceStep> trace
) {
}
