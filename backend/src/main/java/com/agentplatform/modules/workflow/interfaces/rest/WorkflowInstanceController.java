package com.agentplatform.modules.workflow.interfaces.rest;

import com.agentplatform.common.api.ApiResponse;
import com.agentplatform.modules.workflow.application.service.WorkflowExecutionService;
import com.agentplatform.modules.workflow.domain.model.WorkflowInstance;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 流程实例 REST 控制器。
 *
 * <p>所属层：interfaces.rest 接口层。提供实例查询、启动、审批与取消接口，
 * 统一以 {@link ApiResponse} 包装返回；审批是恢复 WAITING_APPROVAL 实例的唯一入口。</p>
 *
 * <p>约束：启动/审批/取消仅允许 {@code PLATFORM_ADMIN}/{@code DEVELOPER} 角色；
 * 查询接口对所有已认证用户开放。</p>
 */
@RestController
@RequestMapping("/api/v1/workflow-instances")
public class WorkflowInstanceController {
    /** 流程执行服务。 */
    private final WorkflowExecutionService execution;

    /**
     * 构造控制器。
     *
     * @param execution 流程执行服务
     */
    public WorkflowInstanceController(WorkflowExecutionService execution) { this.execution = execution; }

    /**
     * 查询全部流程实例。
     *
     * @return 流程实例列表
     */
    @GetMapping
    public ApiResponse<List<WorkflowInstance>> list() { return ApiResponse.ok(execution.list()); }

    /**
     * 查询实例详情及节点运行记录。
     *
     * @param id 流程实例 ID
     * @return 包含 {@code instance} 与 {@code nodeRuns} 的 Map
     * @throws IllegalArgumentException 实例不存在时抛出
     */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> get(@PathVariable String id) {
        WorkflowInstance instance = execution.get(id);
        return ApiResponse.ok(Map.of("instance", instance, "nodeRuns", execution.nodeRuns(id)));
    }

    /**
     * 启动流程实例（异步执行）。
     *
     * @param body 请求体，字段 {@code workflowId} 必填，{@code input} 为可选的输入 JSON 字符串
     * @return 新建的流程实例（状态 RUNNING）
     * @throws IllegalArgumentException 流程定义不存在时抛出
     */
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping
    public ApiResponse<WorkflowInstance> start(@RequestBody Map<String, String> body) {
        return ApiResponse.ok(execution.start(body.get("workflowId"), body.getOrDefault("input", "")));
    }

    /**
     * 审批当前等待节点并恢复执行。
     *
     * @param id   流程实例 ID
     * @param body 可选请求体，字段 {@code comment} 为审批意见
     * @return 恢复后的流程实例（状态 RUNNING）
     * @throws IllegalArgumentException 实例不存在时抛出
     * @throws IllegalStateException    实例不在 WAITING_APPROVAL 状态时抛出
     */
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping("/{id}/approve")
    public ApiResponse<WorkflowInstance> approve(@PathVariable String id, @RequestBody(required = false) Map<String, String> body) {
        return ApiResponse.ok(execution.approve(id, body == null ? null : body.get("comment")));
    }

    /**
     * 取消流程实例。
     *
     * @param id 流程实例 ID
     * @return 取消后的流程实例（状态 CANCELLED）
     * @throws java.util.NoSuchElementException 实例不存在时抛出
     */
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping("/{id}/cancel")
    public ApiResponse<WorkflowInstance> cancel(@PathVariable String id) {
        return ApiResponse.ok(execution.cancel(id));
    }
}
