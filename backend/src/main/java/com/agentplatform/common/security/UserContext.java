package com.agentplatform.common.security;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 当前登录用户的轻量上下文（共享内核）。
 *
 * <p>应用层不直接依赖 Spring Security，由接口层从 JWT 解析后传入。
 * {@code ownerKey} 是写入 {@code chat_sessions.created_by} 的归属标识，
 * 本轮统一使用登录名；{@code userId} 是用户表主键，供后续 P1-8 完整隔离使用。
 * {@code roles} 已去掉 {@code ROLE_} 前缀并统一大写，便于权限判断。</p>
 *
 * @param ownerKey    归属标识（登录名）；内部调用可为 null
 * @param userId      用户主键；OIDC 或内部调用可能为空
 * @param displayName 展示名
 * @param roles       角色列表（PLATFORM_ADMIN / DEVELOPER / VIEWER 等）
 */
public record UserContext(String ownerKey, String userId, String displayName, List<String> roles) {

    /**
     * 规范化角色列表：去空、统一大写并去重，兼容 OIDC 返回小写角色的场景。
     */
    public UserContext {
        roles = roles == null ? List.of() : roles.stream()
                .filter(Objects::nonNull)
                .map(role -> role.trim().toUpperCase(Locale.ROOT))
                .filter(role -> !role.isBlank())
                .distinct()
                .toList();
    }

    /**
     * 判断是否拥有指定角色。
     *
     * @param role 角色编码，例如 PLATFORM_ADMIN
     * @return 拥有时返回 true
     */
    public boolean hasRole(String role) {
        return role != null && roles.contains(role);
    }

    /**
     * 匿名/内部调用上下文：没有归属标识与角色。
     *
     * <p>只用于测试或内部调用；接口层必须使用真实登录上下文。</p>
     *
     * @return 空上下文
     */
    public static UserContext anonymous() {
        return new UserContext(null, null, null, List.of());
    }
}
