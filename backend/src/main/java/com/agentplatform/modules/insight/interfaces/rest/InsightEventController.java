package com.agentplatform.modules.insight.interfaces.rest;

import com.agentplatform.common.api.ApiResponse;
import com.agentplatform.modules.insight.application.query.EventStatsResponse;
import com.agentplatform.modules.insight.application.query.EventStatsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 埋点统计接口（interfaces 层）。
 *
 * <p>只读，供管理员/开发者验证事件是否按预期落库；
 * 不返回事件明细与属性，避免把行为日志变成新的隐私面。</p>
 */
@RestController
@RequestMapping("/api/v1/insight/events")
public class InsightEventController {
    private final EventStatsService stats;

    /**
     * @param stats 事件统计服务
     */
    public InsightEventController(EventStatsService stats) {
        this.stats = stats;
    }

    /**
     * 按事件名统计最近 N 天的事件数。
     *
     * @param days 天数，1–90，默认 7
     * @return 统计结果
     */
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @GetMapping("/stats")
    public ApiResponse<EventStatsResponse> stats(@RequestParam(defaultValue = "7") int days) {
        return ApiResponse.ok(stats.stats(days));
    }
}
