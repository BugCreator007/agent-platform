package com.agentplatform.modules.system.interfaces.dto;

import java.util.List;

/**
 * 当前登录用户 DTO。
 *
 * <p>位于接口层，返回给前端的用户概要信息，不含密码哈希等敏感字段。</p>
 *
 * @param id          用户 ID
 * @param username    登录名
 * @param displayName 展示名
 * @param roles       角色列表
 */
public record AuthUser(String id, String username, String displayName, List<String> roles) {
}
