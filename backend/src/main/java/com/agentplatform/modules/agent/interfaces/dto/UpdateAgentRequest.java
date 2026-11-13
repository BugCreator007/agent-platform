package com.agentplatform.modules.agent.interfaces.dto;

public record UpdateAgentRequest(
        String name,
        String model,
        String providerId,
        String modelId,
        String owner,
        String team,
        String prompt,
        java.util.List<String> tools,
        java.util.List<String> kb
) {
}
