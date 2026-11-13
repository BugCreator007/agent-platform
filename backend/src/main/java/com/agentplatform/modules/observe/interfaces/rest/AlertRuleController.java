package com.agentplatform.modules.observe.interfaces.rest;

import com.agentplatform.common.api.ApiResponse;
import com.agentplatform.modules.observe.application.service.AlertRuleService;
import com.agentplatform.modules.observe.domain.model.AlertRule;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/alert-rules")
public class AlertRuleController {
    private final AlertRuleService service;
    public AlertRuleController(AlertRuleService service) { this.service = service; }
    @GetMapping
    public ApiResponse<List<AlertRule>> list() { return ApiResponse.ok(service.list()); }
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @PutMapping("/{id}")
    public ApiResponse<AlertRule> update(@PathVariable String id, @RequestBody AlertRule rule) {
        return ApiResponse.ok(service.save(new AlertRule(id, rule.name(), rule.metric(), rule.level(),
                rule.thresholdValue(), rule.windowLabel(), rule.channel(), rule.enabled())));
    }
}
