package com.agentplatform.modules.chat.interfaces.rest;

import com.agentplatform.common.api.ApiResponse;
import com.agentplatform.modules.chat.application.query.FeedbackPage;
import com.agentplatform.modules.chat.application.query.FeedbackStats;
import com.agentplatform.modules.chat.application.service.FeedbackApplicationService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * 管理端反馈查询接口（interfaces 层）。
 *
 * <p>基础路径 {@code /api/v1/feedback}，只读；包含用户提问原文与反馈人，
 * 因此仅平台管理员、开发者与评测员可读，只读角色返回 403。</p>
 */
@RestController
@RequestMapping("/api/v1/feedback")
public class FeedbackController {
    private final FeedbackApplicationService feedbacks;

    /**
     * @param feedbacks 反馈应用服务
     */
    public FeedbackController(FeedbackApplicationService feedbacks) {
        this.feedbacks = feedbacks;
    }

    /**
     * 分页查询反馈明细。
     *
     * @param agentId 智能体 ID，可选
     * @param rating  评分过滤，可选
     * @param reason  原因编码过滤，可选
     * @param from    起始时间，可选（默认近 30 天）
     * @param to      截止时间，可选（默认当前）
     * @param page    页码，从 1 开始
     * @param size    每页条数，1–100
     * @return 分页反馈
     */
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER','EVALUATOR')")
    @GetMapping
    public ApiResponse<FeedbackPage> list(
            @RequestParam(required = false) String agentId,
            @RequestParam(required = false) String rating,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(feedbacks.list(agentId, rating, reason, from, to, page, size));
    }

    /**
     * 查询反馈质量汇总。
     *
     * @param agentId 智能体 ID，可选
     * @param days    统计天数，1–90，默认 7
     * @return 汇总数据
     */
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER','EVALUATOR')")
    @GetMapping("/stats")
    public ApiResponse<FeedbackStats> stats(
            @RequestParam(required = false) String agentId,
            @RequestParam(defaultValue = "7") int days) {
        return ApiResponse.ok(feedbacks.stats(agentId, days));
    }
}
