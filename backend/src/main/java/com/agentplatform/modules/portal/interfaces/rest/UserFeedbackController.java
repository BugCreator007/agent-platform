package com.agentplatform.modules.portal.interfaces.rest;

import com.agentplatform.common.api.ApiResponse;
import com.agentplatform.framework.security.CurrentUser;
import com.agentplatform.modules.chat.application.query.FeedbackView;
import com.agentplatform.modules.chat.application.service.FeedbackApplicationService;
import com.agentplatform.modules.chat.application.service.SubmitFeedbackCommand;
import com.agentplatform.modules.portal.interfaces.dto.SubmitFeedbackRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户端消息反馈接口（interfaces 层）。
 *
 * <p>基础路径 {@code /api/v1/user}，只要求登录；反馈人取自 JWT subject，
 * 不接受前端传入。会话归属与应用层校验由 {@link FeedbackApplicationService} 统一处理。</p>
 */
@RestController
@RequestMapping("/api/v1/user")
public class UserFeedbackController {
    private final FeedbackApplicationService feedbacks;

    public UserFeedbackController(FeedbackApplicationService feedbacks) {
        this.feedbacks = feedbacks;
    }

    /**
     * 提交或更新一条反馈。
     *
     * @param messageId 助手消息 ID
     * @param request   反馈内容
     * @param jwt       当前登录用户
     * @return 最新反馈
     */
    @PostMapping("/messages/{messageId}/feedback")
    public ApiResponse<FeedbackView> submit(@PathVariable String messageId,
                                            @Valid @RequestBody SubmitFeedbackRequest request) {
        return ApiResponse.ok(feedbacks.submit(CurrentUser.require().ownerKey(), messageId,
                new SubmitFeedbackCommand(request.rating(), request.reasons(),
                        request.comment(), request.followUpRequested())));
    }

    /**
     * 撤销当前用户对某条消息的反馈。
     *
     * @param messageId 助手消息 ID
     * @param jwt       当前登录用户
     * @return 空响应
     */
    @DeleteMapping("/messages/{messageId}/feedback")
    public ApiResponse<Void> revoke(@PathVariable String messageId) {
        feedbacks.revoke(CurrentUser.require().ownerKey(), messageId);
        return ApiResponse.ok(null);
    }

    /**
     * 查询当前用户在指定会话下的全部反馈，用于刷新后恢复状态。
     *
     * @param sessionId 会话 ID
     * @param jwt       当前登录用户
     * @return 反馈列表
     */
    @GetMapping("/sessions/{sessionId}/feedback")
    public ApiResponse<List<FeedbackView>> list(@PathVariable String sessionId) {
        return ApiResponse.ok(feedbacks.listForUser(CurrentUser.require().ownerKey(), sessionId));
    }
}
