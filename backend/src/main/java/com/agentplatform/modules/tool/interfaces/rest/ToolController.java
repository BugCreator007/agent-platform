package com.agentplatform.modules.tool.interfaces.rest;

import com.agentplatform.common.api.ApiResponse;
import com.agentplatform.modules.tool.application.service.ToolApplicationService;
import com.agentplatform.modules.tool.domain.model.Tool;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tools")
public class ToolController {
    private final ToolApplicationService applicationService;

    public ToolController(ToolApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @GetMapping
    public ApiResponse<List<Tool>> list() { return ApiResponse.ok(applicationService.list()); }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping
    public ApiResponse<Tool> create(@RequestBody java.util.Map<String, Object> body) {
        return ApiResponse.ok(applicationService.create(
                str(body.get("name")), str(body.get("type")), str(body.get("owner")), str(body.get("description")),
                str(body.get("endpoint"))));
    }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping("/mcp")
    public ApiResponse<Tool> addMcp(@RequestBody java.util.Map<String, Object> body) {
        return ApiResponse.ok(applicationService.create(
                str(body.get("name")), "MCP", str(body.get("owner")), str(body.get("description"))));
    }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping("/{name}/toggle")
    public ApiResponse<Tool> toggle(@PathVariable String name) { return ApiResponse.ok(applicationService.toggle(name)); }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping("/check")
    public ApiResponse<List<Tool>> check() { return ApiResponse.ok(applicationService.checkAll()); }

    private static String str(Object value) { return value == null ? null : String.valueOf(value); }
}
