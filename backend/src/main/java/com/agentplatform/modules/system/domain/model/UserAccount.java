package com.agentplatform.modules.system.domain.model;

import java.util.List;

/**
 * 登录用户账号领域模型。
 *
 * <p>位于领域层，承载认证所需的账号信息；{@code passwordHash} 必须是 BCrypt 哈希，
 * 禁止保存或传递明文密码。</p>
 *
 * @param id           用户 ID（UUID），写入 JWT 的 {@code uid} 声明
 * @param username     登录名
 * @param passwordHash BCrypt 密码哈希
 * @param displayName  展示名
 * @param roles        角色列表（如 PLATFORM_ADMIN/DEVELOPER/VIEWER）
 * @param enabled      账号是否启用，禁用后即使密码正确也无法登录
 */
public record UserAccount(String id, String username, String passwordHash, String displayName,
                          List<String> roles, boolean enabled) {
    /**
     * 判断是否拥有指定角色。
     *
     * @param role 角色名
     * @return 拥有该角色返回 {@code true}
     */
    public boolean hasRole(String role) { return roles.contains(role); }
}
