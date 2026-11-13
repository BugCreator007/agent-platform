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

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final TokenBlacklistService blacklist;
    private final AuditService audit;
    @org.springframework.beans.factory.annotation.Value("${app.security.oidc.issuer-uri:}")
    private String oidcIssuer;
    public AuthController(AuthService authService, TokenBlacklistService blacklist, AuditService audit) {
        this.authService = authService; this.blacklist = blacklist; this.audit = audit;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request.username(), request.password()));
    }

    @GetMapping("/me")
    public ApiResponse<AuthUser> me(@AuthenticationPrincipal Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return ApiResponse.ok(new AuthUser(
                jwt.getClaimAsString("uid"),
                jwt.getSubject(),
                jwt.getClaimAsString("name"),
                roles == null ? List.of() : roles));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal Jwt jwt) {
        blacklist.blacklist(jwt.getId(), jwt.getExpiresAt());
        audit.record(jwt.getSubject(), "退出登录", "auth");
        return ApiResponse.ok(null);
    }

    @GetMapping("/config")
    public ApiResponse<Map<String, Object>> config() {
        return ApiResponse.ok(Map.of("localLogin", true, "oidc", oidcIssuer != null && !oidcIssuer.isBlank(), "issuer", oidcIssuer == null ? "" : oidcIssuer));
    }
}
