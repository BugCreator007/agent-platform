package com.agentplatform.modules.tool.interfaces.rest;

import com.agentplatform.common.api.ApiResponse;
import com.agentplatform.modules.tool.application.query.McpCallOutcome;
import com.agentplatform.modules.tool.application.query.McpServerView;
import com.agentplatform.modules.tool.application.query.McpToolView;
import com.agentplatform.modules.tool.application.service.McpServerApplicationService;
import com.agentplatform.modules.tool.interfaces.dto.McpServerRequest;
import com.agentplatform.modules.tool.interfaces.dto.McpToolCallRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class McpController {
    private final McpServerApplicationService service;
    public McpController(McpServerApplicationService service) { this.service = service; }

    @GetMapping("/mcp-servers")
    public ApiResponse<List<McpServerView>> list() { return ApiResponse.ok(service.list()); }

    @GetMapping("/mcp-servers/{id}")
    public ApiResponse<McpServerView> get(@PathVariable String id) { return ApiResponse.ok(service.get(id)); }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping("/mcp-servers")
    public ApiResponse<McpServerView> create(@RequestBody McpServerRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PutMapping("/mcp-servers/{id}")
    public ApiResponse<McpServerView> update(@PathVariable String id, @RequestBody McpServerRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @DeleteMapping("/mcp-servers/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) { service.delete(id); return ApiResponse.ok(null); }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping("/mcp-servers/{id}/connect")
    public ApiResponse<McpServerView> connect(@PathVariable String id) { return ApiResponse.ok(service.connect(id)); }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping("/mcp-servers/{id}/sync")
    public ApiResponse<McpServerView> sync(@PathVariable String id) { return ApiResponse.ok(service.sync(id)); }

    @GetMapping("/mcp-servers/{id}/tools")
    public ApiResponse<List<McpToolView>> tools(@PathVariable String id) { return ApiResponse.ok(service.tools(id)); }

    @GetMapping("/mcp-servers/{id}/calls")
    public ApiResponse<List<?>> calls(@PathVariable String id) { return ApiResponse.ok(service.recentCalls(id, 50)); }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PutMapping("/mcp-tools/{id}/toggle")
    public ApiResponse<McpToolView> toggle(@PathVariable String id) { return ApiResponse.ok(service.toggleTool(id)); }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping("/mcp-tools:call")
    public ApiResponse<McpCallOutcome> call(@RequestBody McpToolCallRequest request) {
        return ApiResponse.ok(service.call(request.toolKey(), request.arguments()));
    }
}
