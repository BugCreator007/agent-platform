package com.agentplatform.framework.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * JWT 注销黑名单校验器。
 *
 * <p>位于技术框架的安全层，作为 {@code NimbusJwtDecoder} 的附加校验器，
 * 在标准校验（签名、过期等）之后检查 Token 的 {@code jti} 是否已被注销。
 * 由于 JWT 无状态，注销只能通过「短期黑名单」实现：登出时把 jti 写入 Redis（TTL 到 Token 过期）。</p>
 */
@Component
public class JwtBlacklistValidator implements OAuth2TokenValidator<Jwt> {
    private final TokenBlacklistService blacklist;

    /**
     * @param blacklist Redis 黑名单服务
     */
    public JwtBlacklistValidator(TokenBlacklistService blacklist) { this.blacklist = blacklist; }

    /**
     * 校验 Token 是否已被注销。
     *
     * @param token 待校验 JWT
     * @return 命中黑名单时返回 {@code invalid_token} 失败，否则成功
     */
    @Override public OAuth2TokenValidatorResult validate(Jwt token) {
        if (blacklist.isBlacklisted(token.getId())) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "token revoked", null));
        }
        return OAuth2TokenValidatorResult.success();
    }
}
