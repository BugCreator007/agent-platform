package com.agentplatform.modules.model.application.query;

public record ProviderTestResult(boolean ok, String status, String latency, String message) {
}
