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

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final AuditService audit;
    private final LoginRateLimiter rateLimiter;
    private final long expiresMinutes;

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

    public LoginResponse login(String username, String password) {
        rateLimiter.check(username);
        UserAccount user = users.findByUsername(username)
                .orElseThrow(() -> {
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
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        audit.record(user.username(), "登录成功", "auth");
        return new LoginResponse(token, "Bearer", expiresMinutes * 60,
                new AuthUser(user.id(), user.username(), user.displayName(), user.roles()));
    }
}
