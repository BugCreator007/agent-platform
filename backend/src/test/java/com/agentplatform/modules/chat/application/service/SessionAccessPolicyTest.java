package com.agentplatform.modules.chat.application.service;

import com.agentplatform.common.security.UserContext;
import com.agentplatform.modules.chat.domain.model.ChatSession;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 会话访问策略单元测试：管理员全量、普通用户本人、历史无主仅管理员。
 */
class SessionAccessPolicyTest {

    /** 管理员与开发者可看全量。 */
    @Test
    void adminsCanReadAll() {
        UserContext admin = new UserContext("admin", "u1", "管理员", List.of("PLATFORM_ADMIN"));
        UserContext developer = new UserContext("dev", "u2", "开发者", List.of("DEVELOPER"));
        ChatSession others = session("bob");
        assertTrue(SessionAccessPolicy.canAccess(admin, others));
        assertTrue(SessionAccessPolicy.canAccess(developer, others));
    }

    /** OIDC 小写角色在 UserContext 构造时统一大写，不影响管理员判断。 */
    @Test
    void lowerCaseRolesNormalized() {
        UserContext admin = new UserContext("admin", "u1", "管理员", List.of("platform_admin"));
        assertTrue(SessionAccessPolicy.canAccess(admin, session("bob")));
    }

    /** 普通用户只能看本人会话，历史无主会话不可见。 */
    @Test
    void viewerOnlyOwnsAndNoLegacy() {
        UserContext viewer = new UserContext("alice", "u3", "用户", List.of("VIEWER"));
        assertTrue(SessionAccessPolicy.canAccess(viewer, session("alice")));
        assertFalse(SessionAccessPolicy.canAccess(viewer, session("bob")));
        assertFalse(SessionAccessPolicy.canAccess(viewer, session(null)));
        assertFalse(SessionAccessPolicy.canAccess(viewer, null));
    }

    private ChatSession session(String owner) {
        return new ChatSession("s_1", "a_1", "助手", "success", 1, 0, 0, 10,
                "10:00:00", "问题", List.of(), owner);
    }
}
