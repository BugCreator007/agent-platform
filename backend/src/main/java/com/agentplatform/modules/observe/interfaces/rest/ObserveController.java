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

@RestController
@RequestMapping("/api/v1")
public class ObserveController {
    private final AlertApplicationService alerts;
    private final CostApplicationService costs;
    private final OverviewApplicationService overview;

    public ObserveController(AlertApplicationService alerts, CostApplicationService costs,
                             OverviewApplicationService overview) {
        this.alerts = alerts;
        this.costs = costs;
        this.overview = overview;
    }

    @GetMapping("/overview")
    public ApiResponse<OverviewResponse> overview() { return ApiResponse.ok(overview.overview()); }

    @GetMapping("/alerts")
    public ApiResponse<List<Alert>> alerts(@RequestParam(required = false) String status) {
        return ApiResponse.ok(alerts.list(status));
    }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping("/alerts/{id}/ack")
    public ApiResponse<Alert> ack(@PathVariable String id) { return ApiResponse.ok(alerts.ack(id)); }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping("/alerts/{id}/resolve")
    public ApiResponse<Alert> resolve(@PathVariable String id) { return ApiResponse.ok(alerts.resolve(id)); }

    @GetMapping("/cost/summary")
    public ApiResponse<CostSummary> cost() { return ApiResponse.ok(costs.summary()); }

    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @PutMapping("/cost/budget")
    public ApiResponse<CostSummary> budget(@RequestBody java.util.Map<String, Object> body) {
        double budget = body.get("budget") instanceof Number n ? n.doubleValue() : 20000;
        return ApiResponse.ok(costs.updateBudget(budget));
    }
}
