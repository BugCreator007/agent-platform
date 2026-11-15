package com.agentplatform.modules.chat.interfaces.rest;

import com.agentplatform.common.api.ApiResponse;
import com.agentplatform.modules.chat.application.query.AgentSessionStat;
import com.agentplatform.modules.chat.application.query.SessionAnalyticsService;
import com.agentplatform.modules.chat.application.service.SessionApplicationService;
import com.agentplatform.modules.chat.domain.model.ChatSession;
import com.agentplatform.modules.chat.domain.model.SessionMessage;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * 会话管理 REST 接口（interfaces 层），供管理后台查询会话、消息与运行指标。
 *
 * <p>读接口返回统一 {@link ApiResponse}；删除接口仅限平台管理员（{@code PLATFORM_ADMIN}）。
 * 指标统计固定按 Asia/Shanghai 时区计算自然日。</p>
 */
@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final SessionApplicationService applicationService;
    private final SessionAnalyticsService analytics;

    public SessionController(SessionApplicationService applicationService,
                             SessionAnalyticsService analytics) {
        this.applicationService = applicationService;
        this.analytics = analytics;
    }

    /**
     * 查询会话列表，支持按状态、智能体与关键字过滤。
     *
     * @param status  会话状态过滤，可为空
     * @param agentId 智能体 ID 过滤，可为空
     * @param q       关键字（匹配主题或会话 ID），可为空
     * @return 会话列表
     */
    @GetMapping
    public ApiResponse<List<ChatSession>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String agentId,
            @RequestParam(required = false, name = "q") String q) {
        return ApiResponse.ok(applicationService.list(status, agentId, q));
    }

    /**
     * 智能体列表/详情使用的真实运行指标，默认统计今天。
     */
    /**
     * 智能体列表/详情使用的真实运行指标，默认统计今天。
     *
     * @param days 统计最近多少天（含今天）；小于等于 0 按 1 天处理，最大 90 天
     * @return 按会话数降序排列的智能体指标列表
     */
    @GetMapping("/stats")
    public ApiResponse<List<AgentSessionStat>> stats(
            @RequestParam(defaultValue = "1") int days) {
        // 限制查询窗口，避免一次拉取过多历史数据拖慢运营概览。
        int range = days <= 0 ? 1 : Math.min(days, 90);
        LocalDate start = LocalDate.now(ZONE).minusDays(range - 1L);
        return ApiResponse.ok(analytics.agentStats(
                start.atStartOfDay(ZONE).toInstant(), Instant.now().plusSeconds(1)));
    }

    /**
     * 查询会话详情。
     *
     * @param id 会话 ID
     * @return 会话聚合（含 trace）
     * @throws com.agentplatform.common.error.BusinessException 会话不存在时抛出
     */
    @GetMapping("/{id}")
    public ApiResponse<ChatSession> get(@PathVariable String id) {
        return ApiResponse.ok(applicationService.get(id));
    }

    /**
     * 删除会话及其全部消息，仅平台管理员可操作。
     *
     * @param id 会话 ID
     * @return 成功响应（无数据）
     * @throws com.agentplatform.common.error.BusinessException 会话不存在时抛出
     */
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @org.springframework.web.bind.annotation.DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        applicationService.delete(id);
        return ApiResponse.ok(null);
    }

    /**
     * 查询会话的消息明细，按创建时间升序。
     *
     * @param id 会话 ID
     * @return 消息列表
     * @throws com.agentplatform.common.error.BusinessException 会话不存在时抛出
     */
    @GetMapping("/{id}/messages")
    public ApiResponse<List<SessionMessage>> messages(@PathVariable String id) {
        return ApiResponse.ok(applicationService.messages(id));
    }
}
