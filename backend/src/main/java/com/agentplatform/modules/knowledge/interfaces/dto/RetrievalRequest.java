package com.agentplatform.modules.knowledge.interfaces.dto;

import jakarta.validation.constraints.NotBlank;

public record RetrievalRequest(@NotBlank String query) {
}
