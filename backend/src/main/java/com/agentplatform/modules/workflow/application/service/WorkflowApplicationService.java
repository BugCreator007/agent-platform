package com.agentplatform.modules.workflow.application.service;

import com.agentplatform.common.error.BusinessException;
import com.agentplatform.common.error.ErrorCode;
import com.agentplatform.modules.workflow.domain.model.Workflow;
import com.agentplatform.modules.workflow.domain.repository.WorkflowInstanceRepository;
import com.agentplatform.modules.workflow.domain.repository.WorkflowRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 流程定义应用服务。
 *
 * <p>所属层：application 应用层。负责流程定义的查询、创建、更新、发布，以及流程运行历史的聚合展示；
 * 不包含执行逻辑，实际运行由 {@link WorkflowExecutionService} 承担。</p>
 *
 * <p>约束：所有按 ID 的读写都经过 {@link #require(String)} 校验存在性，不存在时抛出
 * {@link BusinessException}（错误码 {@code NOT_FOUND}），由统一异常处理转换为 API 响应。</p>
 */
@Service
public class WorkflowApplicationService {
    /** 流程定义仓储端口。 */
    private final WorkflowRepository repository;
    /** 流程实例仓储端口，仅用于查询运行历史。 */
    private final WorkflowInstanceRepository instances;

    /**
     * 构造应用服务。
     *
     * @param repository 流程定义仓储端口
     * @param instances  流程实例仓储端口
     */
    public WorkflowApplicationService(WorkflowRepository repository, WorkflowInstanceRepository instances) {
        this.repository = repository;
        this.instances = instances;
    }

    /**
     * 查询全部流程定义。
     *
     * @return 流程定义列表
     */
    public List<Workflow> list() { return repository.findAll(); }

    /**
     * 按 ID 查询流程定义。
     *
     * @param id 流程 ID
     * @return 流程定义
     * @throws BusinessException 流程不存在时抛出（{@code NOT_FOUND}）
     */
    public Workflow get(String id) { return require(id); }

    /**
     * 创建草稿流程定义。
     *
     * <p>未提供名称/触发方式时使用默认值；新流程初始为草稿、版本 v1、运行次数与成功率为 0。</p>
     *
     * @param name    流程名称，空白时取“未命名流程”
     * @param trigger 触发方式，空白时取“手动触发”
     * @param nodes   节点列表，{@code null} 时视为空列表
     * @return 创建后的流程定义
     */
    public Workflow create(String name, String trigger, List<com.agentplatform.modules.workflow.domain.model.WorkflowNode> nodes) {
        String id = "wf-" + UUID.randomUUID().toString().substring(0, 8);
        Workflow workflow = new Workflow(id, name == null || name.isBlank() ? "未命名流程" : name,
                "draft", trigger == null || trigger.isBlank() ? "手动触发" : trigger,
                0, 0, "顾先路", "v1", "刚刚", nodes == null ? List.of() : nodes);
        repository.save(workflow);
        return workflow;
    }

    /**
     * 更新流程定义的基础信息与节点列表。
     *
     * <p>采用“读取-重建-保存”方式：状态、运行统计、负责人和版本沿用当前值，
     * 仅名称、触发方式、节点列表可被覆盖；空参数保留原值，避免部分更新误清空数据。</p>
     *
     * @param id      流程 ID
     * @param name    新名称，空白时保留原名称
     * @param trigger 新触发方式，空白时保留原值
     * @param nodes   新节点列表，{@code null} 时保留原节点
     * @return 更新后的流程定义
     * @throws BusinessException 流程不存在时抛出（{@code NOT_FOUND}）
     */
    public Workflow update(String id, String name, String trigger, List<com.agentplatform.modules.workflow.domain.model.WorkflowNode> nodes) {
        Workflow current = require(id);
        Workflow next = new Workflow(current.id(), name == null || name.isBlank() ? current.name() : name,
                current.status(), trigger == null || trigger.isBlank() ? current.trigger() : trigger,
                current.runs(), current.success(), current.owner(), current.version(), "刚刚",
                nodes == null ? current.nodes() : nodes);
        repository.save(next);
        return next;
    }

    /**
     * 查询某流程最近的运行记录（最多 20 条，按开始时间倒序）。
     *
     * <p>仓储层没有按流程过滤的查询，因此在应用层全量读取后过滤；返回面向界面的精简字段。</p>
     *
     * @param id 流程 ID
     * @return 运行记录行，包含 id、流程名、状态、耗时与开始时间
     * @throws BusinessException 流程不存在时抛出（{@code NOT_FOUND}）
     */
    public List<Map<String, Object>> runs(String id) {
        Workflow workflow = require(id);
        return instances.findAll().stream()
                .filter(i -> workflow.id().equals(i.workflowId()))
                .sorted((a, b) -> b.startedAt().compareTo(a.startedAt()))
                .limit(20)
                .map(i -> {
                    Map<String, Object> row = new java.util.LinkedHashMap<>();
                    row.put("id", i.id());
                    row.put("workflowName", i.workflowName());
                    row.put("status", i.status().toLowerCase());
                    // 未结束的实例没有耗时，统一展示为“运行中”。
                    row.put("duration", i.finishedAt() == null ? "运行中"
                            : ((i.finishedAt().toEpochMilli() - i.startedAt().toEpochMilli()) + " ms"));
                    row.put("time", i.startedAt().toString());
                    return row;
                })
                .toList();
    }

    /**
     * 校验流程存在并返回，供“测试运行”接口在启动实例前做前置检查。
     *
     * @param id 流程 ID
     * @return 流程定义
     * @throws BusinessException 流程不存在时抛出（{@code NOT_FOUND}）
     */
    public Workflow test(String id) {
        Workflow workflow = require(id);
        return workflow;
    }

    /**
     * 发布流程：状态置为已发布并自增版本号。
     *
     * @param id 流程 ID
     * @return 发布后的流程定义
     * @throws BusinessException 流程不存在时抛出（{@code NOT_FOUND}）
     */
    public Workflow publish(String id) {
        Workflow workflow = require(id);
        workflow.publish();
        repository.save(workflow);
        return workflow;
    }

    /**
     * 按 ID 读取流程，不存在时抛出业务异常。
     *
     * @param id 流程 ID
     * @return 流程定义
     * @throws BusinessException 流程不存在时抛出（{@code NOT_FOUND}）
     */
    private Workflow require(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "流程不存在: " + id));
    }
}
