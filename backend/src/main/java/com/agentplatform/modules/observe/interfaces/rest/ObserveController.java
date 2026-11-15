package com.agentplatform.modules.observe.interfaces.rest;

import com.agentplatform.common.api.ApiResponse;
import com.agentplatform.modules.observe.application.query.OverviewResponse;
import com.agentplatform.modules.observe.application.service.AlertApplicationService;
import com.agentplatform.modules.observe.application.service.CostApplicationService;
import com.agentplatform.modules.observe.application.service.OverviewApplicationService;
import com.agentplatform.modules.observe.domain.model.Alert;
import com.agentplatform.modules.observe.domain.model.CostSummary;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 可观测 REST 控制器（interfaces 层）。
 *
 * <p>暴露运营概览、告警确认/恢复、成本汇总与预算调整接口。
 * 读接口对登录用户开放；告警确认/恢复限 PLATFORM_ADMIN/DEVELOPER，
 * 预算调整仅限 PLATFORM_ADMIN。统一返回 {@link ApiResponse}。</p>
 */
@RestController
@RequestMapping("/api/v1")
public class ObserveController {
    private final AlertApplicationService alerts;
    private final CostApplicationService costs;
    private final OverviewApplicationService overview;

    /**
     * 构造控制器。
     *
     * @param alerts   告警应用服务
     * @param costs    成本应用服务
     * @param overview 概览聚合服务
     */
    public ObserveController(AlertApplicationService alerts, CostApplicationService costs,
                             OverviewApplicationService overview) {
        this.alerts = alerts;
        this.costs = costs;
        this.overview = overview;
    }

    /**
     * 查询运营概览。
     *
     * @param range 统计天数，默认 7，服务层限制最大 90
     * @return 概览响应
     */
    @GetMapping("/overview")
    public ApiResponse<OverviewResponse> overview(
            @RequestParam(defaultValue = "7") int range) {
        return ApiResponse.ok(overview.overview(range));
    }

    /**
     * 查询告警列表。
     *
     * @param status 可选状态过滤（active/ack/resolved）
     * @return 告警列表
     */
    @GetMapping("/alerts")
    public ApiResponse<List<Alert>> alerts(@RequestParam(required = false) String status) {
        return ApiResponse.ok(alerts.list(status));
    }

    /**
     * 确认告警。
     *
     * @param id 告警 ID
     * @return 确认后的告警
     */
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping("/alerts/{id}/ack")
    public ApiResponse<Alert> ack(@PathVariable String id) { return ApiResponse.ok(alerts.ack(id)); }

    /**
     * 恢复告警。
     *
     * @param id 告警 ID
     * @return 恢复后的告警
     */
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping("/alerts/{id}/resolve")
    public ApiResponse<Alert> resolve(@PathVariable String id) { return ApiResponse.ok(alerts.resolve(id)); }

    /**
     * 查询成本汇总。
     *
     * @return 成本汇总
     */
    @GetMapping("/cost/summary")
    public ApiResponse<CostSummary> cost() { return ApiResponse.ok(costs.summary()); }

    /**
     * 更新月度预算。
     *
     * @param body 请求体：budget，缺省或非数值时按 20000 处理
     * @return 更新后的成本汇总
     */
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @PutMapping("/cost/budget")
    public ApiResponse<CostSummary> budget(@RequestBody java.util.Map<String, Object> body) {
        double budget = body.get("budget") instanceof Number n ? n.doubleValue() : 20000;
        return ApiResponse.ok(costs.updateBudget(budget));
    }
}
