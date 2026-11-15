package com.agentplatform.modules.system.interfaces.dto;

/**
 * 登录响应 DTO。
 *
 * <p>位于接口层，返回访问 Token 与用户信息。前端应将 Token 放入
 * {@code Authorization: Bearer <token>} 请求头。</p>
 *
 * @param accessToken 访问 Token（HS256 JWT）
 * @param tokenType   Token 类型，固定 {@code Bearer}
 * @param expiresIn   有效期（秒）
 * @param user        当前用户信息
 */
public record LoginResponse(String accessToken, String tokenType, long expiresIn, AuthUser user) {
}
