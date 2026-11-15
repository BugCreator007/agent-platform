package com.agentplatform.modules.workflow.interfaces.rest;

import com.agentplatform.common.api.ApiResponse;
import com.agentplatform.modules.workflow.application.service.WorkflowApplicationService;
import com.agentplatform.modules.workflow.application.service.WorkflowExecutionService;
import com.agentplatform.modules.workflow.domain.model.Workflow;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 流程定义 REST 控制器。
 *
 * <p>所属层：interfaces.rest 接口层。对外提供流程定义的查询、创建、更新、发布、
 * 运行历史以及“测试运行”接口，统一以 {@link ApiResponse} 包装返回。</p>
 *
 * <p>约束：写接口仅允许 {@code PLATFORM_ADMIN}/{@code DEVELOPER} 角色；
 * 创建/更新的请求体使用宽松的 Map 结构以兼容前端画布数据，节点在 {@link #nodes(Object)}
 * 中转换为领域对象，非法元素被忽略而不是报错。</p>
 */
@RestController
@RequestMapping("/api/v1/workflows")
public class WorkflowController {
    /** 流程定义应用服务。 */
    private final WorkflowApplicationService applicationService;
    /** 流程执行服务，仅用于“测试运行”启动实例。 */
    private final WorkflowExecutionService execution;

    /**
     * 构造控制器。
     *
     * @param applicationService 流程定义应用服务
     * @param execution          流程执行服务
     */
    public WorkflowController(WorkflowApplicationService applicationService, WorkflowExecutionService execution) {
        this.applicationService = applicationService;
        this.execution = execution;
    }

    /**
     * 查询全部流程定义。
     *
     * @return 流程定义列表
     */
    @GetMapping
    public ApiResponse<List<Workflow>> list() { return ApiResponse.ok(applicationService.list()); }

    /**
     * 按 ID 查询流程定义。
     *
     * @param id 流程 ID
     * @return 流程定义
     * @throws com.agentplatform.common.error.BusinessException 流程不存在时抛出
     */
    @GetMapping("/{id}")
    public ApiResponse<Workflow> get(@PathVariable String id) { return ApiResponse.ok(applicationService.get(id)); }

    /**
     * 创建草稿流程。
     *
     * <p>请求体字段：{@code name}、{@code trigger}、{@code nodes}（节点数组，
     * 每项包含 key/type/title/meta/config）。</p>
     *
     * @param body 创建请求体
     * @return 创建后的流程定义
     */
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping
    public ApiResponse<Workflow> create(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(applicationService.create(str(body.get("name")), str(body.get("trigger")), nodes(body.get("nodes"))));
    }

    /**
     * 更新流程定义。
     *
     * @param id   流程 ID
     * @param body 更新请求体，字段同创建接口
     * @return 更新后的流程定义
     * @throws com.agentplatform.common.error.BusinessException 流程不存在时抛出
     */
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PutMapping("/{id}")
    public ApiResponse<Workflow> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(applicationService.update(id, str(body.get("name")), str(body.get("trigger")), nodes(body.get("nodes"))));
    }

    /**
     * 查询流程最近运行记录。
     *
     * @param id 流程 ID
     * @return 运行记录行（最多 20 条）
     * @throws com.agentplatform.common.error.BusinessException 流程不存在时抛出
     */
    @GetMapping("/{id}/runs")
    public ApiResponse<List<Map<String, Object>>> runs(@PathVariable String id) {
        return ApiResponse.ok(applicationService.runs(id));
    }

    /**
     * 测试运行流程：立即启动一个实例并返回启动结果。
     *
     * <p>未提供输入时使用 {@code {"test":true}} 作为默认输入；接口只返回启动快照，
     * 实际执行异步进行，可通过实例接口查看进度。</p>
     *
     * @param id   流程 ID
     * @param body 可选请求体，字段 {@code input} 为启动输入 JSON 字符串
     * @return 包含流程名、实例状态、实例 ID 与节点数的摘要
     * @throws IllegalArgumentException 流程定义不存在时抛出
     */
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping("/{id}/test")
    public ApiResponse<Map<String, Object>> test(@PathVariable String id,
                                                 @RequestBody(required = false) Map<String, Object> body) {
        Workflow workflow = applicationService.test(id);
        String input = body == null ? null : str(body.get("input"));
        if (input == null || input.isBlank()) input = "{\"test\":true}";
        var instance = execution.start(id, input);
        return ApiResponse.ok(Map.of("workflow", workflow.name(), "status", instance.status(),
                "instanceId", instance.id(), "nodes", workflow.nodes().size()));
    }

    /**
     * 发布流程，状态置为 published 并自增版本号。
     *
     * @param id 流程 ID
     * @return 发布后的流程定义
     * @throws com.agentplatform.common.error.BusinessException 流程不存在时抛出
     */
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping("/{id}/publish")
    public ApiResponse<Workflow> publish(@PathVariable String id) { return ApiResponse.ok(applicationService.publish(id)); }

    /** 将任意请求体值转为字符串，{@code null} 保持为 {@code null}。 */
    private static String str(Object value) { return value == null ? null : String.valueOf(value); }

    /**
     * 将前端节点数组转换为领域节点列表。
     *
     * <p>非 List 输入或非 Map 元素被忽略；缺少 config 时使用空配置，
     * 保持接口对不完整前端数据的容错。</p>
     */
    @SuppressWarnings("unchecked")
    private static List<com.agentplatform.modules.workflow.domain.model.WorkflowNode> nodes(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream()
                .filter(item -> item instanceof Map<?, ?>)
                .map(item -> {
                    Map<String, Object> m = (Map<String, Object>) item;
                    Map<String, Object> config = m.get("config") instanceof Map<?, ?> c
                            ? (Map<String, Object>) c : Map.of();
                    return new com.agentplatform.modules.workflow.domain.model.WorkflowNode(
                            str(m.get("key")), str(m.get("type")), str(m.get("title")), str(m.get("meta")), config);
                })
                .toList();
    }
}
