package com.agentplatform.modules.model.interfaces.rest;

import com.agentplatform.common.api.ApiResponse;
import com.agentplatform.modules.model.application.query.ProviderTestResult;
import com.agentplatform.modules.model.application.service.ModelApplicationService;
import com.agentplatform.modules.model.domain.model.ModelInfo;
import com.agentplatform.modules.model.domain.model.Provider;
import com.agentplatform.modules.model.domain.model.RoutingPolicy;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class ModelController {
    private final ModelApplicationService applicationService;

    public ModelController(ModelApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @GetMapping("/providers")
    public ApiResponse<List<Provider>> providers() {
        return ApiResponse.ok(applicationService.providers());
    }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping("/providers")
    public ApiResponse<Provider> create(@RequestBody java.util.Map<String, Object> body) {
        return ApiResponse.ok(applicationService.create(
                str(body.get("name")), str(body.get("endpoint")), str(body.get("region")),
                str(body.get("key")), number(body.get("quota"), 10000), str(body.get("protocol"))));
    }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PutMapping("/providers/{id}/quota")
    public ApiResponse<Provider> updateQuota(@PathVariable String id, @RequestBody java.util.Map<String, Object> body) {
        return ApiResponse.ok(applicationService.updateQuota(id, number(body.get("quota"), 10000)));
    }

    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @DeleteMapping("/providers/{id}")
    public ApiResponse<Void> deleteProvider(@PathVariable String id) {
        applicationService.deleteProvider(id);
        return ApiResponse.ok(null);
    }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping("/providers/{id}/test")
    public ApiResponse<ProviderTestResult> test(@PathVariable String id) {
        return ApiResponse.ok(applicationService.test(id));
    }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PutMapping("/providers/{id}/key")
    public ApiResponse<Provider> updateKey(@PathVariable String id, @RequestBody java.util.Map<String, Object> body) {
        return ApiResponse.ok(applicationService.updateKey(id, str(body.get("key"))));
    }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping("/providers/{id}/rotate-key")
    public ApiResponse<Provider> rotateKey(@PathVariable String id, @RequestBody java.util.Map<String, Object> body) {
        return ApiResponse.ok(applicationService.updateKey(id, str(body.get("key"))));
    }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping("/providers/{id}/toggle")
    public ApiResponse<Provider> toggle(@PathVariable String id) {
        return ApiResponse.ok(applicationService.toggle(id));
    }

    @GetMapping("/models")
    public ApiResponse<List<ModelInfo>> models(@RequestParam(required = false) String providerId) {
        return ApiResponse.ok(applicationService.models(providerId));
    }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping("/models")
    public ApiResponse<ModelInfo> createModel(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(applicationService.createModel(
                str(body.get("id")), str(body.get("providerId")), str(body.get("name")),
                str(body.get("context")), number(body.get("inPrice"), 0), number(body.get("outPrice"), 0),
                str(body.get("upstreamModel")), strings(body.get("capabilities")),
                (int) number(body.get("routeWeight"), 0), str(body.get("status"))));
    }

    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @DeleteMapping("/models/{id}")
    public ApiResponse<Void> deleteModel(@PathVariable String id) {
        applicationService.deleteModel(id);
        return ApiResponse.ok(null);
    }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping("/models/{id}/default")
    public ApiResponse<ModelInfo> setDefault(@PathVariable String id) {
        return ApiResponse.ok(applicationService.setDefault(id));
    }

    @GetMapping("/models/routing")
    public ApiResponse<RoutingPolicy> routing() {
        return ApiResponse.ok(applicationService.routing());
    }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PutMapping("/models/routing")
    public ApiResponse<RoutingPolicy> updateRouting(@RequestBody RoutingPolicy policy) {
        return ApiResponse.ok(applicationService.updateRouting(policy));
    }

    private static String str(Object value) { return value == null ? null : String.valueOf(value); }

    private static double number(Object value, double fallback) {
        if (value instanceof Number n) return n.doubleValue();
        try {
            return value == null ? fallback : Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(item -> item != null && !String.valueOf(item).isBlank())
                .map(String::valueOf).toList();
    }
}
