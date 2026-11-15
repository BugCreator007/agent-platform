package com.agentplatform.modules.workflow.domain.repository;

import com.agentplatform.modules.workflow.domain.model.WorkflowInstance;
import com.agentplatform.modules.workflow.domain.model.WorkflowNodeRun;

import java.util.List;
import java.util.Optional;

/**
 * 流程实例与节点运行记录仓储端口（domain 层）。
 *
 * <p>由 infrastructure 层适配器实现。执行引擎依赖该端口读写执行游标与节点记录，
 * 以保证等待态（审批/定时器/子流程）和进程重启后的续跑能力。</p>
 */
public interface WorkflowInstanceRepository {
    /**
     * 新增或更新流程实例。
     *
     * @param instance 流程实例
     * @return 持久化后的实例（与入参语义一致）
     */
    WorkflowInstance save(WorkflowInstance instance);

    /**
     * 新增或更新节点运行记录。
     *
     * @param nodeRun 节点运行记录
     * @return 持久化后的记录
     */
    WorkflowNodeRun saveNodeRun(WorkflowNodeRun nodeRun);

    /**
     * 按 ID 查询流程实例。
     *
     * @param id 实例 ID
     * @return 流程实例（可能为空）
     */
    Optional<WorkflowInstance> findById(String id);

    /**
     * 查询全部流程实例，调度器据此扫描等待态实例。
     *
     * @return 流程实例列表
     */
    List<WorkflowInstance> findAll();

    /**
     * 查询某实例的全部节点运行记录，按开始时间升序。
     *
     * @param instanceId 实例 ID
     * @return 节点运行记录列表
     */
    List<WorkflowNodeRun> findNodeRuns(String instanceId);
}
