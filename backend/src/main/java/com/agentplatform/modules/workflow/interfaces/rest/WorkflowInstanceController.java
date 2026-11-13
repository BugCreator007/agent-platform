package com.agentplatform.modules.workflow.interfaces.rest;

import com.agentplatform.common.api.ApiResponse;
import com.agentplatform.modules.workflow.application.service.WorkflowExecutionService;
import com.agentplatform.modules.workflow.domain.model.WorkflowInstance;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/workflow-instances")
public class WorkflowInstanceController {
    private final WorkflowExecutionService execution;
    public WorkflowInstanceController(WorkflowExecutionService execution) { this.execution = execution; }

    @GetMapping
    public ApiResponse<List<WorkflowInstance>> list() { return ApiResponse.ok(execution.list()); }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> get(@PathVariable String id) {
        WorkflowInstance instance = execution.get(id);
        return ApiResponse.ok(Map.of("instance", instance, "nodeRuns", execution.nodeRuns(id)));
    }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping
    public ApiResponse<WorkflowInstance> start(@RequestBody Map<String, String> body) {
        return ApiResponse.ok(execution.start(body.get("workflowId"), body.getOrDefault("input", "")));
    }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping("/{id}/approve")
    public ApiResponse<WorkflowInstance> approve(@PathVariable String id, @RequestBody(required = false) Map<String, String> body) {
        return ApiResponse.ok(execution.approve(id, body == null ? null : body.get("comment")));
    }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping("/{id}/cancel")
    public ApiResponse<WorkflowInstance> cancel(@PathVariable String id) {
        return ApiResponse.ok(execution.cancel(id));
    }
}
