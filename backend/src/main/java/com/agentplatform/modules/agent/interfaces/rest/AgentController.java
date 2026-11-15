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

/**
 * 智能体管理 REST 控制器（interfaces/rest 层）。
 * <p>基础路径 {@code /api/v1/agents}，所有响应统一包装为 {@link ApiResponse}。
 * 读接口对已认证用户开放；写接口要求 {@code PLATFORM_ADMIN} 或 {@code DEVELOPER}，
 * 删除仅限 {@code PLATFORM_ADMIN}。写操作由全局审计过滤器记录。</p>
 */
@RestController
@RequestMapping("/api/v1/agents")
public class AgentController {
    private final AgentApplicationService applicationService;

    /**
     * @param applicationService 智能体应用服务
     */
    public AgentController(AgentApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 查询智能体列表。
     *
     * @param status 可选状态过滤（draft/running/grayscale/paused，忽略大小写）
     * @param q      可选关键字，匹配名称、编码、负责人或团队
     * @return 智能体视图列表
     */
    @GetMapping
    public ApiResponse<List<AgentSummary>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false, name = "q") String q) {
        return ApiResponse.ok(applicationService.list(status, q));
    }

    /**
     * 创建智能体（草稿状态）。
     *
     * @param request 创建请求，经 Bean Validation 校验
     * @return 创建后的智能体视图
     * @throws com.agentplatform.common.error.BusinessException 编码冲突或校验失败时抛出
     */
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping
    public ApiResponse<AgentSummary> create(@Valid @RequestBody CreateAgentRequest request) {
        return ApiResponse.ok(applicationService.create(request));
    }

    /**
     * 查询智能体详情。
     *
     * @param id 智能体唯一标识
     * @return 智能体视图
     * @throws com.agentplatform.common.error.BusinessException id 不存在时抛出 404
     */
    @GetMapping("/{id}")
    public ApiResponse<AgentSummary> get(@PathVariable String id) {
        return ApiResponse.ok(applicationService.get(id));
    }

    /**
     * 局部更新智能体配置。
     *
     * @param id      智能体唯一标识
     * @param request 更新请求（null 字段表示不修改）
     * @return 更新后的智能体视图
     * @throws com.agentplatform.common.error.BusinessException id 不存在时抛出 404
     */
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PutMapping("/{id}")
    public ApiResponse<AgentSummary> update(@PathVariable String id, @RequestBody UpdateAgentRequest request) {
        return ApiResponse.ok(applicationService.update(id, request));
    }

    /**
     * 删除智能体，仅平台管理员可操作。
     *
     * @param id 智能体唯一标识
     * @return 空数据的统一响应
     * @throws com.agentplatform.common.error.BusinessException id 不存在时抛出 404
     */
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        applicationService.delete(id);
        return ApiResponse.ok(null);
    }

    /**
     * 发布智能体。
     *
     * @param id 智能体唯一标识
     * @return 发布后的智能体视图
     * @throws com.agentplatform.common.error.BusinessException id 不存在时抛出 404
     */
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping("/{id}/publish")
    public ApiResponse<AgentSummary> publish(@PathVariable String id) {
        return ApiResponse.ok(applicationService.publish(id));
    }

    /**
     * 暂停智能体。
     *
     * @param id 智能体唯一标识
     * @return 暂停后的智能体视图
     * @throws com.agentplatform.common.error.BusinessException id 不存在时抛出 404
     */
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping("/{id}/pause")
    public ApiResponse<AgentSummary> pause(@PathVariable String id) {
        return ApiResponse.ok(applicationService.pause(id));
    }

    /**
     * 恢复已暂停的智能体。
     *
     * @param id 智能体唯一标识
     * @return 恢复后的智能体视图
     * @throws com.agentplatform.common.error.BusinessException id 不存在时抛出 404
     */
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping("/{id}/resume")
    public ApiResponse<AgentSummary> resume(@PathVariable String id) {
        return ApiResponse.ok(applicationService.resume(id));
    }
}
