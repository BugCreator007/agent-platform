package com.agentplatform.framework.security;

import com.agentplatform.common.error.BusinessException;
import com.agentplatform.common.error.ErrorCode;
import com.agentplatform.common.security.UserContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.Objects;

/**
 * 从 Spring Security 上下文读取当前用户（技术框架层）。
 *
 * <p>本地 JWT 的 {@code subject} 是登录名；OIDC Token 可能使用不透明 subject，
 * 因此优先取 {@code preferred_username}，再回退 subject，保证会话归属与反馈人稳定。
 * 角色从授权列表提取并去掉 {@code ROLE_} 前缀。</p>
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    /**
     * 读取当前登录用户；未登录时抛出 401。
     *
     * @return 当前用户上下文
     * @throws BusinessException 未认证时抛出 {@link ErrorCode#UNAUTHENTICATED}
     */
    public static UserContext require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            Jwt jwt = jwtAuthentication.getToken();
            String ownerKey = firstNonBlank(jwt.getClaimAsString("preferred_username"), jwt.getSubject());
            String userId = jwt.getClaimAsString("uid");
            String displayName = jwt.getClaimAsString("name");
            return new UserContext(ownerKey, userId, displayName,
                    roles(authentication.getAuthorities()));
        }
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getName())) {
            return new UserContext(authentication.getName(), null, authentication.getName(),
                    roles(authentication.getAuthorities()));
        }
        throw new BusinessException(ErrorCode.UNAUTHENTICATED, "未登录或登录已过期");
    }

    /**
     * 把 Spring Security 授权转换为角色编码。
     *
     * @param authorities 授权列表
     * @return 去重后的角色列表
     */
    private static List<String> roles(java.util.Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(Objects::nonNull)
                .map(authority -> authority.startsWith("ROLE_") ? authority.substring(5) : authority)
                .distinct()
                .toList();
    }

    /**
     * 返回第一个非空白字符串。
     *
     * @param values 候选值
     * @return 首个非空白值；都为空白时返回 null
     */
    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }
}
