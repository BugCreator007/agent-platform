package com.agentplatform.modules.chat.application.service;

import com.agentplatform.common.security.UserContext;
import com.agentplatform.modules.chat.domain.model.ChatSession;

import java.util.Set;

/**
 * 会话访问策略（application 层）。
 *
 * <p>统一判定管理端与运行时的会话可见性：平台管理员、开发者可看全量；
 * 其他角色只能看自己创建的会话；历史无主会话（{@code created_by} 为空）
 * 仅管理员/开发者可见，普通用户不可续聊、不可反馈。</p>
 */
public final class SessionAccessPolicy {
    /** 可查看全量会话的角色。 */
    private static final Set<String> FULL_READERS = Set.of("PLATFORM_ADMIN", "DEVELOPER");

    private SessionAccessPolicy() {
    }

    /**
     * 判断用户是否可查看全部会话。
     *
     * @param user 当前用户
     * @return 管理员或开发者返回 true
     */
    public static boolean canReadAll(UserContext user) {
        return user != null && user.roles().stream().anyMatch(FULL_READERS::contains);
    }

    /**
     * 判断用户是否可访问指定会话。
     *
     * @param user    当前用户
     * @param session 目标会话
     * @return 可访问时返回 true；历史无主会话仅管理员/开发者可访问
     */
    public static boolean canAccess(UserContext user, ChatSession session) {
        if (user == null || session == null) return false;
        if (canReadAll(user)) return true;
        return session.createdBy() != null && !session.createdBy().isBlank()
                && session.createdBy().equals(user.ownerKey());
    }

    /**
     * 判断用户是否可续聊指定会话。
     *
     * <p>与读取共用同一策略，避免出现“能看不能聊”或反之的口径分叉。</p>
     *
     * @param user    当前用户
     * @param session 目标会话
     * @return 可续聊时返回 true
     */
    public static boolean canResume(UserContext user, ChatSession session) {
        return canAccess(user, session);
    }
}
