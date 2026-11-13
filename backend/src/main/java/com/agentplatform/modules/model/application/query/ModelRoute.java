package com.agentplatform.modules.model.application.query;

public record ModelRoute(
        String providerId,
        String providerName,
        String protocol,
        String baseUrl,
        String apiKey,
        String modelId,
        String modelName,
        String upstreamModel,
        String providerStatus,
        String modelStatus,
        double inPrice,
        double outPrice,
        int routeWeight,
        long latencyMs
) {
    public boolean available() {
        return apiKey != null && !apiKey.isBlank()
                && !"paused".equalsIgnoreCase(providerStatus)
                && !"paused".equalsIgnoreCase(modelStatus);
    }

    public String label() {
        return providerName + " / " + modelName;
    }
}
