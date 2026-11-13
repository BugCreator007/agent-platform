package com.agentplatform.modules.agent.interfaces.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateAgentRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 64) String code,
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
