package com.agentplatform.modules.release.interfaces.rest;

import com.agentplatform.common.api.ApiResponse;
import com.agentplatform.modules.release.application.service.ReleaseApplicationService;
import com.agentplatform.modules.release.domain.model.Release;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 发布管理 REST 控制器（interfaces/rest 层）。
 * <p>基础路径 {@code /api/v1/releases}，响应统一包装为 {@link ApiResponse}。
 * 查询对已认证用户开放；审批、推进、流量调整、回滚要求 {@code PLATFORM_ADMIN}
 * 或 {@code DEVELOPER}，写操作由全局审计过滤器记录。</p>
 */
@RestController
@RequestMapping("/api/v1/releases")
public class ReleaseController {
    private final ReleaseApplicationService applicationService;

    /**
     * @param applicationService 发布应用服务
     */
    public ReleaseController(ReleaseApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 查询全部发布单。
     *
     * @return 发布记录列表
     */
    @GetMapping
    public ApiResponse<List<Release>> list() { return ApiResponse.ok(applicationService.list()); }

    /**
     * 审批发布单（会触发评测发布门禁）。
     *
     * @param id 发布单唯一标识
     * @return 审批后的发布记录
     * @throws com.agentplatform.common.error.BusinessException id 不存在、门禁未通过或状态不允许时抛出
     */
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping("/{id}/approve")
    public ApiResponse<Release> approve(@PathVariable String id) { return ApiResponse.ok(applicationService.approve(id)); }

    /**
     * 推进发布阶段（草稿提交审批或灰度放量至全量）。
     *
     * @param id 发布单唯一标识
     * @return 推进后的发布记录
     * @throws com.agentplatform.common.error.BusinessException id 不存在或状态不允许推进时抛出
     */
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping("/{id}/promote")
    public ApiResponse<Release> promote(@PathVariable String id) { return ApiResponse.ok(applicationService.promote(id)); }

    /**
     * 调整发布流量比例。
     * <p>请求体约定为 {@code {"traffic": <number>}}；缺失或非数字时按 0 处理，
     * 越界值由领域聚合夹紧到 0-100。</p>
     *
     * @param id   发布单唯一标识
     * @param body 请求体，读取 {@code traffic} 字段
     * @return 更新后的发布记录
     * @throws com.agentplatform.common.error.BusinessException id 不存在时抛出 404
     */
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PutMapping("/{id}/traffic")
    public ApiResponse<Release> traffic(@PathVariable String id, @RequestBody java.util.Map<String, Object> body) {
        // 使用 Map 接收而非强类型 DTO：兼容前端传数字/字符串的松散格式
        int traffic = body.get("traffic") instanceof Number n ? n.intValue() : 0;
        return ApiResponse.ok(applicationService.updateTraffic(id, traffic));
    }

    /**
     * 回滚发布单。
     *
     * @param id 发布单唯一标识
     * @return 回滚后的发布记录
     * @throws com.agentplatform.common.error.BusinessException id 不存在时抛出 404
     */
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping("/{id}/rollback")
    public ApiResponse<Release> rollback(@PathVariable String id) { return ApiResponse.ok(applicationService.rollback(id)); }
}
