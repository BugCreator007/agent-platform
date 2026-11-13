package com.agentplatform.modules.model.domain.model;

import java.util.List;

public record RoutingPolicy(String strategy, String primary, List<String> fallback, String region) {
}
