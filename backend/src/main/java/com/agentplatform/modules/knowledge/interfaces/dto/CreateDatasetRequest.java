package com.agentplatform.modules.knowledge.interfaces.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateDatasetRequest(
        @NotBlank String name,
        String owner,
        String description,
        String embeddingModel,
        String indexType,
        String retrievalMode
) {
}
