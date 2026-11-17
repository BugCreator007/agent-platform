package com.agentplatform.modules.portal.interfaces.rest;

import com.agentplatform.common.api.ApiResponse;
import com.agentplatform.common.security.UserContext;
import com.agentplatform.framework.security.CurrentUser;
import com.agentplatform.modules.chat.application.query.UserMessageView;
import com.agentplatform.modules.chat.application.service.SessionApplicationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户端会话读取接口（portal interfaces 层）。
 *
 * <p>只暴露本人会话的消息，归属校验在 {@link SessionApplicationService} 内完成；
 * 不提供管理端的全量会话列表与统计入口。用户端从此接口读取历史，
 * 不再直接调用管理端 {@code /api/v1/sessions/{id}/messages}。</p>
 */
@RestController
@RequestMapping("/api/v1/user/sessions")
public class UserSessionController {
    private final SessionApplicationService sessions;

    /**
     * @param sessions 会话应用服务（chat 模块公开 API）
     */
    public UserSessionController(SessionApplicationService sessions) {
        this.sessions = sessions;
    }

    /**
     * 查询本人会话的消息明细。
     *
     * @param id 会话 ID
     * @return 按创建时间升序的消息列表
     */
    @GetMapping("/{id}/messages")
    public ApiResponse<List<UserMessageView>> messages(@PathVariable String id) {
        UserContext user = CurrentUser.require();
        return ApiResponse.ok(sessions.messagesForUser(user, id));
    }
}
