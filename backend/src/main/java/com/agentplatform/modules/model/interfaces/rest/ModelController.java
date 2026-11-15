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

/**
 * 模型厂商 REST 控制器（interfaces 层）。
 *
 * <p>负责厂商与模型的查询、创建、删除、密钥轮换、连通性测试以及路由策略维护。
 * 读接口对已登录用户开放；写接口通过 {@code @PreAuthorize} 限制为
 * PLATFORM_ADMIN/DEVELOPER，删除厂商/模型仅限 PLATFORM_ADMIN。
 * 所有响应统一包装为 {@link ApiResponse}，写操作由审计过滤器记录。</p>
 */
@RestController
@RequestMapping("/api/v1")
public class ModelController {
    private final ModelApplicationService applicationService;

    /**
     * 构造控制器。
     *
     * @param applicationService 模型应用服务
     */
    public ModelController(ModelApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 查询全部厂商。
     *
     * @return 厂商列表（密钥脱敏）
     */
    @GetMapping("/providers")
    public ApiResponse<List<Provider>> providers() {
        return ApiResponse.ok(applicationService.providers());
    }

    /**
     * 新建厂商。
     *
     * @param body 请求体：name/endpoint/region/key/quota/protocol
     * @return 新建的厂商
     */
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping("/providers")
    public ApiResponse<Provider> create(@RequestBody java.util.Map<String, Object> body) {
        return ApiResponse.ok(applicationService.create(
                str(body.get("name")), str(body.get("endpoint")), str(body.get("region")),
                str(body.get("key")), number(body.get("quota"), 10000), str(body.get("protocol"))));
    }

    /**
     * 更新厂商额度。
     *
     * @param id   厂商 ID
     * @param body 请求体：quota
     * @return 更新后的厂商
     */
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PutMapping("/providers/{id}/quota")
    public ApiResponse<Provider> updateQuota(@PathVariable String id, @RequestBody java.util.Map<String, Object> body) {
        return ApiResponse.ok(applicationService.updateQuota(id, number(body.get("quota"), 10000)));
    }

    /**
     * 删除厂商及其名下模型。
     *
     * @param id 厂商 ID
     * @return 空响应
     */
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @DeleteMapping("/providers/{id}")
    public ApiResponse<Void> deleteProvider(@PathVariable String id) {
        applicationService.deleteProvider(id);
        return ApiResponse.ok(null);
    }

    /**
     * 测试厂商连通性。
     *
     * @param id 厂商 ID
     * @return 探测结果
     */
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping("/providers/{id}/test")
    public ApiResponse<ProviderTestResult> test(@PathVariable String id) {
        return ApiResponse.ok(applicationService.test(id));
    }

    /**
     * 更新厂商 API Key。
     *
     * @param id   厂商 ID
     * @param body 请求体：key（明文，仅传输一次，落库加密）
     * @return 更新后的厂商（密钥脱敏）
     */
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PutMapping("/providers/{id}/key")
    public ApiResponse<Provider> updateKey(@PathVariable String id, @RequestBody java.util.Map<String, Object> body) {
        return ApiResponse.ok(applicationService.updateKey(id, str(body.get("key"))));
    }

    /**
     * 轮换厂商 API Key（与更新密钥同语义，语义化入口）。
     *
     * @param id   厂商 ID
     * @param body 请求体：key
     * @return 更新后的厂商（密钥脱敏）
     */
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping("/providers/{id}/rotate-key")
    public ApiResponse<Provider> rotateKey(@PathVariable String id, @RequestBody java.util.Map<String, Object> body) {
        return ApiResponse.ok(applicationService.updateKey(id, str(body.get("key"))));
    }

    /**
     * 启用/停用厂商。
     *
     * @param id 厂商 ID
     * @return 切换后的厂商
     */
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping("/providers/{id}/toggle")
    public ApiResponse<Provider> toggle(@PathVariable String id) {
        return ApiResponse.ok(applicationService.toggle(id));
    }

    /**
     * 查询模型列表。
     *
     * @param providerId 可选，按厂商过滤
     * @return 模型列表
     */
    @GetMapping("/models")
    public ApiResponse<List<ModelInfo>> models(@RequestParam(required = false) String providerId) {
        return ApiResponse.ok(applicationService.models(providerId));
    }

    /**
     * 新建模型。
     *
     * @param body 请求体：id/providerId/name/context/inPrice/outPrice/upstreamModel/capabilities/routeWeight/status
     * @return 新建的模型
     */
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping("/models")
    public ApiResponse<ModelInfo> createModel(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(applicationService.createModel(
                str(body.get("id")), str(body.get("providerId")), str(body.get("name")),
                str(body.get("context")), number(body.get("inPrice"), 0), number(body.get("outPrice"), 0),
                str(body.get("upstreamModel")), strings(body.get("capabilities")),
                (int) number(body.get("routeWeight"), 0), str(body.get("status"))));
    }

    /**
     * 删除模型。
     *
     * @param id 模型 ID
     * @return 空响应
     */
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @DeleteMapping("/models/{id}")
    public ApiResponse<Void> deleteModel(@PathVariable String id) {
        applicationService.deleteModel(id);
        return ApiResponse.ok(null);
    }

    /**
     * 将模型设为默认模型（同时成为路由主模型）。
     *
     * @param id 模型 ID
     * @return 设为默认后的模型
     */
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping("/models/{id}/default")
    public ApiResponse<ModelInfo> setDefault(@PathVariable String id) {
        return ApiResponse.ok(applicationService.setDefault(id));
    }

    /**
     * 查询全局路由策略。
     *
     * @return 路由策略
     */
    @GetMapping("/models/routing")
    public ApiResponse<RoutingPolicy> routing() {
        return ApiResponse.ok(applicationService.routing());
    }

    /**
     * 更新全局路由策略。
     *
     * @param policy 新的路由策略（主模型、回退链、策略、区域）
     * @return 规范化后的路由策略
     */
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PutMapping("/models/routing")
    public ApiResponse<RoutingPolicy> updateRouting(@RequestBody RoutingPolicy policy) {
        return ApiResponse.ok(applicationService.updateRouting(policy));
    }

    /**
     * 把请求体中的任意值转为字符串，null 原样返回。
     */
    private static String str(Object value) { return value == null ? null : String.valueOf(value); }

    /**
     * 把请求体中的数值安全转换为 double，无法解析时使用默认值。
     */
    private static double number(Object value, double fallback) {
        if (value instanceof Number n) return n.doubleValue();
        try {
            return value == null ? fallback : Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * 把请求体中的数组转为非空字符串列表，非数组时返回空列表。
     */
    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(item -> item != null && !String.valueOf(item).isBlank())
                .map(String::valueOf).toList();
    }
}
