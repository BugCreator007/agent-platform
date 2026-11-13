package com.agentplatform.modules.knowledge.interfaces.dto;

import jakarta.validation.constraints.NotBlank;

public record UploadDocumentRequest(@NotBlank String name, Long chunks, String size) {
}
