package com.agentplatform.modules.chat.domain.model;

import java.time.Instant;

public record SessionMessage(String id, String sessionId, String role, String content,
                             int tokens, String model, int contextCount, Instant createdAt) {
}
