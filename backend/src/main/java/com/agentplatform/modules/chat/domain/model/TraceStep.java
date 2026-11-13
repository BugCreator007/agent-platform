package com.agentplatform.modules.chat.domain.model;

public record TraceStep(String kind, String name, long ms, int tokens, String status, String detail) {
}
