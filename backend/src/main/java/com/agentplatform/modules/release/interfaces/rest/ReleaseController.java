package com.agentplatform.modules.release.interfaces.rest;

import com.agentplatform.common.api.ApiResponse;
import com.agentplatform.modules.release.application.service.ReleaseApplicationService;
import com.agentplatform.modules.release.domain.model.Release;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/releases")
public class ReleaseController {
    private final ReleaseApplicationService applicationService;

    public ReleaseController(ReleaseApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @GetMapping
    public ApiResponse<List<Release>> list() { return ApiResponse.ok(applicationService.list()); }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping("/{id}/approve")
    public ApiResponse<Release> approve(@PathVariable String id) { return ApiResponse.ok(applicationService.approve(id)); }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping("/{id}/promote")
    public ApiResponse<Release> promote(@PathVariable String id) { return ApiResponse.ok(applicationService.promote(id)); }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PutMapping("/{id}/traffic")
    public ApiResponse<Release> traffic(@PathVariable String id, @RequestBody java.util.Map<String, Object> body) {
        int traffic = body.get("traffic") instanceof Number n ? n.intValue() : 0;
        return ApiResponse.ok(applicationService.updateTraffic(id, traffic));
    }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping("/{id}/rollback")
    public ApiResponse<Release> rollback(@PathVariable String id) { return ApiResponse.ok(applicationService.rollback(id)); }
}
