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
    @GetMapping("/stats")
    public ApiResponse<List<AgentSessionStat>> stats(
            @RequestParam(defaultValue = "1") int days) {
        int range = days <= 0 ? 1 : Math.min(days, 90);
        LocalDate start = LocalDate.now(ZONE).minusDays(range - 1L);
        return ApiResponse.ok(analytics.agentStats(
                start.atStartOfDay(ZONE).toInstant(), Instant.now().plusSeconds(1)));
    }

    @GetMapping("/{id}")
    public ApiResponse<ChatSession> get(@PathVariable String id) {
        return ApiResponse.ok(applicationService.get(id));
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @org.springframework.web.bind.annotation.DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        applicationService.delete(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{id}/messages")
    public ApiResponse<List<SessionMessage>> messages(@PathVariable String id) {
        return ApiResponse.ok(applicationService.messages(id));
    }
}
