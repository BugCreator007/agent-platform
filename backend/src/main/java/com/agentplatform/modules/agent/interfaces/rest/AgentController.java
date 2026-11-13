package com.agentplatform.modules.agent.interfaces.rest;

import com.agentplatform.common.api.ApiResponse;
import com.agentplatform.modules.agent.application.query.AgentSummary;
import com.agentplatform.modules.agent.application.service.AgentApplicationService;
import com.agentplatform.modules.agent.interfaces.dto.CreateAgentRequest;
import com.agentplatform.modules.agent.interfaces.dto.UpdateAgentRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/agents")
public class AgentController {
    private final AgentApplicationService applicationService;

    public AgentController(AgentApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @GetMapping
    public ApiResponse<List<AgentSummary>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false, name = "q") String q) {
        return ApiResponse.ok(applicationService.list(status, q));
    }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping
    public ApiResponse<AgentSummary> create(@Valid @RequestBody CreateAgentRequest request) {
        return ApiResponse.ok(applicationService.create(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<AgentSummary> get(@PathVariable String id) {
        return ApiResponse.ok(applicationService.get(id));
    }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PutMapping("/{id}")
    public ApiResponse<AgentSummary> update(@PathVariable String id, @RequestBody UpdateAgentRequest request) {
        return ApiResponse.ok(applicationService.update(id, request));
    }

    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        applicationService.delete(id);
        return ApiResponse.ok(null);
    }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping("/{id}/publish")
    public ApiResponse<AgentSummary> publish(@PathVariable String id) {
        return ApiResponse.ok(applicationService.publish(id));
    }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping("/{id}/pause")
    public ApiResponse<AgentSummary> pause(@PathVariable String id) {
        return ApiResponse.ok(applicationService.pause(id));
    }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping("/{id}/resume")
    public ApiResponse<AgentSummary> resume(@PathVariable String id) {
        return ApiResponse.ok(applicationService.resume(id));
    }
}
