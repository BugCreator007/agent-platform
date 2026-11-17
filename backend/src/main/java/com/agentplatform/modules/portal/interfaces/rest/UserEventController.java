package com.agentplatform.modules.portal.interfaces.rest;

import com.agentplatform.common.api.ApiResponse;
import com.agentplatform.common.error.BusinessException;
import com.agentplatform.common.error.ErrorCode;
import com.agentplatform.common.security.UserContext;
import com.agentplatform.framework.security.CurrentUser;
import com.agentplatform.modules.chat.application.service.SessionApplicationService;
import com.agentplatform.modules.insight.application.service.ProductEventRecorder;
import com.agentplatform.modules.portal.interfaces.dto.SubmitEventRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户端客户端事件上报接口（portal interfaces 层）。
 *
 * <p>只接受白名单事件；携带会话的事件先校验会话归属，用户身份由服务端覆盖，
 * 不接受前端传入 userId。事件写入异步且失败降级，不影响页面主流程。</p>
 */
@RestController
@RequestMapping("/api/v1/user")
public class UserEventController {
    private final ProductEventRecorder events;
    private final SessionApplicationService sessions;

    /**
     * @param events   埋点记录入口（insight 模块公开 API）
     * @param sessions 会话应用服务，用于校验事件归属
     */
    public UserEventController(ProductEventRecorder events, SessionApplicationService sessions) {
        this.events = events;
        this.sessions = sessions;
    }

    /**
     * 上报一条客户端事件。
     *
     * @param request 事件内容
     * @return 成功响应（无数据）
     * @throws BusinessException 事件名不允许、属性非法或无权访问会话时抛出
     */
    @PostMapping("/events")
    public ApiResponse<Void> track(@Valid @RequestBody SubmitEventRequest request) {
        UserContext user = CurrentUser.require();
        boolean hasSession = request.sessionId() != null && !request.sessionId().isBlank();
        boolean hasMessage = request.messageId() != null && !request.messageId().isBlank();
        if (hasMessage && !hasSession) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "消息事件必须携带会话");
        }
        if (hasSession) {
            if (hasMessage) {
                // 消息必须存在且属于该会话，防止把采纳事件挂到他人消息上。
                sessions.ensureMessageInSession(user, request.sessionId(), request.messageId());
            } else {
                // 非本人或历史无主会话统一按不存在处理，避免通过事件接口探测会话。
                sessions.ensureAccessible(user, request.sessionId());
            }
        }
        events.recordClientEvent(request.name(), request.eventId(), user.ownerKey(),
                request.sessionId(), request.messageId(), request.agentId(), request.properties());
        return ApiResponse.ok(null);
    }
}
