package com.agentplatform.modules.system.interfaces.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求 DTO。
 *
 * <p>位于接口层，由 {@code @Valid} 触发字段校验；
 * 仅承载登录凭据，服务端不记录该请求体。</p>
 *
 * @param username 登录名，不能为空
 * @param password 明文密码，不能为空（仅在内存中与 BCrypt 哈希比对）
 */
public record LoginRequest(@NotBlank String username, @NotBlank String password) {
}
