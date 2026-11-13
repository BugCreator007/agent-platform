package com.agentplatform.modules.system.interfaces.dto;

public record LoginResponse(String accessToken, String tokenType, long expiresIn, AuthUser user) {
}
