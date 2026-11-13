package com.agentplatform.framework.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class JwtBlacklistValidator implements OAuth2TokenValidator<Jwt> {
    private final TokenBlacklistService blacklist;
    public JwtBlacklistValidator(TokenBlacklistService blacklist) { this.blacklist = blacklist; }
    @Override public OAuth2TokenValidatorResult validate(Jwt token) {
        if (blacklist.isBlacklisted(token.getId())) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "token revoked", null));
        }
        return OAuth2TokenValidatorResult.success();
    }
}
