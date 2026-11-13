package com.agentplatform.modules.system.interfaces.rest;

import com.agentplatform.common.api.ApiResponse;
import com.agentplatform.modules.system.application.service.SystemApplicationService;
import com.agentplatform.modules.system.domain.model.ApiKey;
import com.agentplatform.modules.system.domain.model.AuditLog;
import com.agentplatform.modules.system.domain.model.Member;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class SystemApiController {
    private final SystemApplicationService applicationService;

    public SystemApiController(SystemApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @GetMapping("/members")
    public ApiResponse<List<Member>> members() { return ApiResponse.ok(applicationService.members()); }

    @PutMapping("/members/{name}/role")
    public ApiResponse<Member> role(@PathVariable String name, @RequestBody Map<String, String> body) {
        return ApiResponse.ok(applicationService.changeRole(name, body.getOrDefault("role", "只读成员")));
    }

    @PostMapping("/members")
    public ApiResponse<Member> invite(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(applicationService.invite(str(body.get("name")), str(body.get("email")), str(body.get("role"))));
    }

    @GetMapping("/api-keys")
    public ApiResponse<List<ApiKey>> keys() { return ApiResponse.ok(applicationService.keys()); }

    @PostMapping("/api-keys")
    public ApiResponse<ApiKey> createKey(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(applicationService.createKey(str(body.get("name")), str(body.get("scope")), "顾先路"));
    }

    @GetMapping("/audit-logs")
    public ApiResponse<List<AuditLog>> audit() { return ApiResponse.ok(applicationService.audit()); }

    private static String str(Object value) { return value == null ? null : String.valueOf(value); }
}
