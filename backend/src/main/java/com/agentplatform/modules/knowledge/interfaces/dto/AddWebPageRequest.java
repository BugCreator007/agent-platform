package com.agentplatform.modules.knowledge.interfaces.dto;

import jakarta.validation.constraints.NotBlank;

public record AddWebPageRequest(@NotBlank String url, String name, String cookie) {
}
