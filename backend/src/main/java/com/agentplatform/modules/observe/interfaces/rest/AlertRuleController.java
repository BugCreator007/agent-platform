package com.agentplatform.modules.observe.interfaces.rest;

import com.agentplatform.common.api.ApiResponse;
import com.agentplatform.modules.observe.application.service.AlertRuleService;
import com.agentplatform.modules.observe.domain.model.AlertRule;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 告警规则 REST 控制器（interfaces 层）。
 *
 * <p>提供规则查询与更新：查询对所有登录用户开放，更新仅限 PLATFORM_ADMIN。
 * 更新时以路径 ID 为准，忽略请求体中的 id。</p>
 */
@RestController
@RequestMapping("/api/v1/alert-rules")
public class AlertRuleController {
    private final AlertRuleService service;

    /**
     * 构造控制器。
     *
     * @param service 告警规则服务
     */
    public AlertRuleController(AlertRuleService service) { this.service = service; }

    /**
     * 查询全部告警规则。
     *
     * @return 规则列表
     */
    @GetMapping
    public ApiResponse<List<AlertRule>> list() { return ApiResponse.ok(service.list()); }

    /**
     * 更新告警规则（以路径 ID 覆盖请求体 id）。
     *
     * @param id   规则 ID
     * @param rule 请求体中的规则内容
     * @return 保存后的规则
     */
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @PutMapping("/{id}")
    public ApiResponse<AlertRule> update(@PathVariable String id, @RequestBody AlertRule rule) {
        return ApiResponse.ok(service.save(new AlertRule(id, rule.name(), rule.metric(), rule.level(),
                rule.thresholdValue(), rule.windowLabel(), rule.channel(), rule.enabled())));
    }
}
