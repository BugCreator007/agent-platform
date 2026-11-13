package com.agentplatform.modules.system.domain.model;

public record ApiKey(String name, String key, String scope, String created, String last, String by) {
}
