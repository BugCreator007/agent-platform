package com.agentplatform.modules.eval.interfaces.rest;

import com.agentplatform.common.api.ApiResponse;
import com.agentplatform.modules.eval.application.service.EvalApplicationService;
import com.agentplatform.modules.eval.domain.model.EvalRun;
import com.agentplatform.modules.eval.domain.model.EvalSuite;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class EvalController {
    private final EvalApplicationService applicationService;

    public EvalController(EvalApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @GetMapping("/eval-suites")
    public ApiResponse<List<EvalSuite>> suites() { return ApiResponse.ok(applicationService.suites()); }

    @GetMapping("/eval-runs")
    public ApiResponse<List<EvalRun>> runs() { return ApiResponse.ok(applicationService.runs()); }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER','EVALUATOR')")
    @PostMapping("/eval-runs")
    public ApiResponse<EvalRun> run(@RequestBody Map<String, String> body) {
        return ApiResponse.ok(applicationService.run(
                body.getOrDefault("suiteId", "ev-cs"),
                body.getOrDefault("agentName", "售后工单助手"),
                body.getOrDefault("version", "v25")));
    }
}
