package com.agentplatform.modules.eval.interfaces.rest;

import com.agentplatform.common.api.ApiResponse;
import com.agentplatform.modules.eval.application.service.EvalApplicationService;
import com.agentplatform.modules.eval.domain.model.EvalRun;
import com.agentplatform.modules.eval.domain.model.EvalSuite;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 评测中心 REST 控制器（interfaces/rest 层）。
 * <p>基础路径 {@code /api/v1}，响应统一包装为 {@link ApiResponse}。
 * 查询接口对已认证用户开放；触发评测要求 {@code PLATFORM_ADMIN}、{@code DEVELOPER}
 * 或 {@code EVALUATOR} 角色。</p>
 */
@RestController
@RequestMapping("/api/v1")
public class EvalController {
    private final EvalApplicationService applicationService;

    /**
     * @param applicationService 评测应用服务
     */
    public EvalController(EvalApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 查询全部评测集。
     *
     * @return 评测集列表
     */
    @GetMapping("/eval-suites")
    public ApiResponse<List<EvalSuite>> suites() { return ApiResponse.ok(applicationService.suites()); }

    /**
     * 查询全部评测运行记录。
     *
     * @return 运行记录列表
     */
    @GetMapping("/eval-runs")
    public ApiResponse<List<EvalRun>> runs() { return ApiResponse.ok(applicationService.runs()); }

    /**
     * 触发一次评测运行。
     * <p>请求体使用松散的 Map 契约（suiteId/agentName/version），缺失字段回退到
     * 演示默认值（ev-cs / 售后工单助手 / v25），便于前端快速联调。</p>
     *
     * @param body 请求体，可选字段 suiteId、agentName、version
     * @return 新建的评测运行记录
     */
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER','EVALUATOR')")
    @PostMapping("/eval-runs")
    public ApiResponse<EvalRun> run(@RequestBody Map<String, String> body) {
        return ApiResponse.ok(applicationService.run(
                body.getOrDefault("suiteId", "ev-cs"),
                body.getOrDefault("agentName", "售后工单助手"),
                body.getOrDefault("version", "v25")));
    }
}
