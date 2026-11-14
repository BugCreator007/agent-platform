package com.agentplatform.modules.workflow.interfaces.rest;

import com.agentplatform.common.api.ApiResponse;
import com.agentplatform.modules.workflow.application.service.WorkflowApplicationService;
import com.agentplatform.modules.workflow.application.service.WorkflowExecutionService;
import com.agentplatform.modules.workflow.domain.model.Workflow;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/workflows")
public class WorkflowController {
    private final WorkflowApplicationService applicationService;
    private final WorkflowExecutionService execution;

    public WorkflowController(WorkflowApplicationService applicationService, WorkflowExecutionService execution) {
        this.applicationService = applicationService;
        this.execution = execution;
    }

    @GetMapping
    public ApiResponse<List<Workflow>> list() { return ApiResponse.ok(applicationService.list()); }

    @GetMapping("/{id}")
    public ApiResponse<Workflow> get(@PathVariable String id) { return ApiResponse.ok(applicationService.get(id)); }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping
    public ApiResponse<Workflow> create(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(applicationService.create(str(body.get("name")), str(body.get("trigger")), nodes(body.get("nodes"))));
    }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PutMapping("/{id}")
    public ApiResponse<Workflow> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(applicationService.update(id, str(body.get("name")), str(body.get("trigger")), nodes(body.get("nodes"))));
    }

    @GetMapping("/{id}/runs")
    public ApiResponse<List<Map<String, Object>>> runs(@PathVariable String id) {
        return ApiResponse.ok(applicationService.runs(id));
    }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping("/{id}/test")
    public ApiResponse<Map<String, Object>> test(@PathVariable String id,
                                                 @RequestBody(required = false) Map<String, Object> body) {
        Workflow workflow = applicationService.test(id);
        String input = body == null ? null : str(body.get("input"));
        if (input == null || input.isBlank()) input = "{\"test\":true}";
        var instance = execution.start(id, input);
        return ApiResponse.ok(Map.of("workflow", workflow.name(), "status", instance.status(),
                "instanceId", instance.id(), "nodes", workflow.nodes().size()));
    }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping("/{id}/publish")
    public ApiResponse<Workflow> publish(@PathVariable String id) { return ApiResponse.ok(applicationService.publish(id)); }

    private static String str(Object value) { return value == null ? null : String.valueOf(value); }

    @SuppressWarnings("unchecked")
    private static List<com.agentplatform.modules.workflow.domain.model.WorkflowNode> nodes(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream()
                .filter(item -> item instanceof Map<?, ?>)
                .map(item -> {
                    Map<String, Object> m = (Map<String, Object>) item;
                    Map<String, Object> config = m.get("config") instanceof Map<?, ?> c
                            ? (Map<String, Object>) c : Map.of();
                    return new com.agentplatform.modules.workflow.domain.model.WorkflowNode(
                            str(m.get("key")), str(m.get("type")), str(m.get("title")), str(m.get("meta")), config);
                })
                .toList();
    }
}
