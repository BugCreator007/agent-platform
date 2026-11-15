package com.agentplatform.modules.system.application.auth;

import com.agentplatform.common.error.BusinessException;
import com.agentplatform.common.error.ErrorCode;
import com.agentplatform.modules.system.domain.model.UserAccount;
import com.agentplatform.modules.system.domain.repository.UserRepository;
import com.agentplatform.modules.system.interfaces.dto.AuthUser;
import com.agentplatform.modules.system.interfaces.dto.LoginResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.*;
import com.agentplatform.framework.security.LoginRateLimiter;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 认证用例服务。
 *
 * <p>位于应用层，负责本地账号登录：限流检查 → 账号查找 → BCrypt 密码校验 →
 * 签发 HS256 JWT（claims 含 uid/name/roles/jti）→ 记录审计。
 * 安全约束：用户名不存在与密码错误返回同一提示，避免账号枚举；
 * 失败计数由 {@link LoginRateLimiter} 维护，登录成功即清零。</p>
 */
@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final AuditService audit;
    private final LoginRateLimiter rateLimiter;
    /** Token 有效期（分钟），由 app.security.jwt.expires-minutes 配置 */
    private final long expiresMinutes;

    /**
     * @param users            用户仓储端口
     * @param passwordEncoder  BCrypt 密码编码器
     * @param jwtEncoder       JWT 编码器
     * @param audit            审计服务
     * @param rateLimiter      登录失败限流器
     * @param expiresMinutes   Token 有效期（分钟）
     */
    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder,
                       AuditService audit, LoginRateLimiter rateLimiter,
                       @Value("${app.security.jwt.expires-minutes:480}") long expiresMinutes) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.audit = audit;
        this.rateLimiter = rateLimiter;
        this.expiresMinutes = expiresMinutes;
    }

    /**
     * 执行登录并签发访问 Token。
     *
     * @param username 登录名
     * @param password 明文密码（仅在内存中与 BCrypt 哈希比对，不落库）
     * @return 访问 Token、有效期与用户信息
     * @throws BusinessException 限流锁定、账号不存在/禁用或密码错误时抛出（401/403）
     */
    public LoginResponse login(String username, String password) {
        rateLimiter.check(username);
        UserAccount user = users.findByUsername(username)
                .orElseThrow(() -> {
                    // 账号不存在也计一次失败，避免攻击者靠提示差异枚举用户名
                    rateLimiter.recordFailure(username);
                    throw new BusinessException(ErrorCode.UNAUTHENTICATED, "用户名或密码错误");
                });
        if (!user.enabled() || !passwordEncoder.matches(password, user.passwordHash())) {
            rateLimiter.recordFailure(username);
            audit.record(username, "登录失败", "auth");
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "用户名或密码错误");
        }
        rateLimiter.clear(username);
        Instant now = Instant.now();
        Instant expires = now.plus(expiresMinutes, ChronoUnit.MINUTES);
        // jti（.id）是 Token 唯一标识，登出时写入 Redis 黑名单用于吊销
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("agent-platform")
                .subject(user.username())
                .issuedAt(now)
                .expiresAt(expires)
                .claim("uid", user.id())
                .claim("name", user.displayName())
                .claim("roles", user.roles())
                .id(java.util.UUID.randomUUID().toString())
                .build();
        // HS256 对称签名：与 SecurityConfig 中的 JwtDecoder 使用同一密钥校验
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        audit.record(user.username(), "登录成功", "auth");
        // expiresIn 按 OAuth2 惯例返回秒数，供前端刷新/过期判断
        return new LoginResponse(token, "Bearer", expiresMinutes * 60,
                new AuthUser(user.id(), user.username(), user.displayName(), user.roles()));
    }
}
