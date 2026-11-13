package com.agentplatform.modules.observe.domain.model;

public record AlertRule(String id, String name, String metric, String level, double thresholdValue,
                        String windowLabel, String channel, boolean enabled) {
}
