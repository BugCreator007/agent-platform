package com.agentplatform.modules.system.domain.model;

public record AuditLog(String who, String action, String object, String time, String ip) {
}
