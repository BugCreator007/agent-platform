package com.agentplatform.modules.system.interfaces.rest;

import com.agentplatform.common.api.ApiResponse;
import com.agentplatform.modules.system.application.auth.AuthService;
import com.agentplatform.modules.system.application.auth.AuditService;
import com.agentplatform.framework.security.TokenBlacklistService;
import com.agentplatform.modules.system.interfaces.dto.AuthUser;
import com.agentplatform.modules.system.interfaces.dto.LoginRequest;
import com.agentplatform.modules.system.interfaces.dto.LoginResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 认证接口控制器。
 *
 * <p>位于接口层，提供登录、当前用户、登出与登录方式配置接口。
 * 登录/配置为白名单接口；me/logout 需要 Bearer Token。
 * 登出通过把 Token 的 jti 写入 Redis 黑名单实现即时吊销。</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final TokenBlacklistService blacklist;
    private final AuditService audit;
    /** OIDC issuer 配置，用于告知前端是否启用外部登录 */
    @org.springframework.beans.factory.annotation.Value("${app.security.oidc.issuer-uri:}")
    private String oidcIssuer;

    /**
     * @param authService 认证用例服务
     * @param blacklist   Token 黑名单服务
     * @param audit       审计服务
     */
    public AuthController(AuthService authService, TokenBlacklistService blacklist, AuditService audit) {
        this.authService = authService; this.blacklist = blacklist; this.audit = audit;
    }

    /**
     * 账号密码登录。
     *
     * @param request 登录请求（用户名/密码非空校验）
     * @return 访问 Token 与用户信息
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request.username(), request.password()));
    }

    /**
     * 查询当前登录用户。
     *
     * <p>信息直接取自 JWT claims（uid/name/roles），不额外查库。</p>
     *
     * @param jwt 已认证的 JWT
     * @return 当前用户信息
     */
    @GetMapping("/me")
    public ApiResponse<AuthUser> me(@AuthenticationPrincipal Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return ApiResponse.ok(new AuthUser(
                jwt.getClaimAsString("uid"),
                jwt.getSubject(),
                jwt.getClaimAsString("name"),
                roles == null ? List.of() : roles));
    }

    /**
     * 登出：将当前 Token 的 jti 加入黑名单，使未过期的 Token 立即失效。
     *
     * @param jwt 已认证的 JWT
     * @return 空响应
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal Jwt jwt) {
        blacklist.blacklist(jwt.getId(), jwt.getExpiresAt());
        audit.record(jwt.getSubject(), "退出登录", "auth");
        return ApiResponse.ok(null);
    }

    /**
     * 查询登录方式配置。
     *
     * <p>告知前端是否支持本地账号登录、是否启用了 OIDC 及其 issuer。</p>
     *
     * @return 登录方式配置
     */
    @GetMapping("/config")
    public ApiResponse<Map<String, Object>> config() {
        return ApiResponse.ok(Map.of("localLogin", true, "oidc", oidcIssuer != null && !oidcIssuer.isBlank(), "issuer", oidcIssuer == null ? "" : oidcIssuer));
    }
}
