package com.agentplatform.modules.workflow.infrastructure.persistence.repository;

import com.agentplatform.modules.workflow.domain.model.WorkflowInstance;
import com.agentplatform.modules.workflow.domain.model.WorkflowNodeRun;
import com.agentplatform.modules.workflow.domain.repository.WorkflowInstanceRepository;
import com.agentplatform.modules.workflow.infrastructure.persistence.entity.WorkflowInstanceEntity;
import com.agentplatform.modules.workflow.infrastructure.persistence.entity.WorkflowNodeRunEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 流程实例领域端口的多表适配器。
 *
 * <p>所属层：infrastructure 基础设施层。实现 domain 层 {@link WorkflowInstanceRepository}，
 * 聚合实例仓储与节点运行记录仓储，完成实体与领域模型的转换。应用层仅依赖该端口，
 * 不感知本类及 MyBatis 细节。</p>
 */
@Repository
public class MybatisWorkflowInstanceRepository implements WorkflowInstanceRepository {
    /** 流程实例实体仓储。 */
    private final com.agentplatform.modules.workflow.infrastructure.persistence.repository.WorkflowInstanceRepository instances;
    /** 节点运行记录实体仓储。 */
    private final WorkflowNodeRunRepository nodeRuns;

    /**
     * 构造适配器。
     *
     * @param instances 流程实例实体仓储
     * @param nodeRuns  节点运行记录实体仓储
     */
    public MybatisWorkflowInstanceRepository(
            com.agentplatform.modules.workflow.infrastructure.persistence.repository.WorkflowInstanceRepository instances,
            WorkflowNodeRunRepository nodeRuns) {
        this.instances = instances; this.nodeRuns = nodeRuns;
    }

    /**
     * 转换并保存流程实例。
     *
     * @param instance 流程实例
     * @return 保存后的流程实例
     */
    @Override public WorkflowInstance save(WorkflowInstance instance) {
        WorkflowInstanceEntity entity = WorkflowInstanceEntity.from(instance);
        instances.save(entity);
        return entity.toDomain();
    }

    /**
     * 转换并保存节点运行记录。
     *
     * @param nodeRun 节点运行记录
     * @return 保存后的记录
     */
    @Override public WorkflowNodeRun saveNodeRun(WorkflowNodeRun nodeRun) {
        WorkflowNodeRunEntity entity = WorkflowNodeRunEntity.from(nodeRun);
        nodeRuns.save(entity);
        return entity.toDomain();
    }

    /**
     * 按 ID 查询流程实例。
     *
     * @param id 实例 ID
     * @return 流程实例（可能为空）
     */
    @Override public Optional<WorkflowInstance> findById(String id) { return instances.findById(id).map(WorkflowInstanceEntity::toDomain); }

    /**
     * 查询全部流程实例。
     *
     * @return 流程实例列表
     */
    @Override public List<WorkflowInstance> findAll() { return instances.findAll().stream().map(WorkflowInstanceEntity::toDomain).toList(); }

    /**
     * 查询某实例的节点运行记录。
     *
     * @param instanceId 实例 ID
     * @return 节点运行记录列表（按开始时间升序）
     */
    @Override public List<WorkflowNodeRun> findNodeRuns(String instanceId) { return nodeRuns.findByInstance(instanceId).stream().map(WorkflowNodeRunEntity::toDomain).toList(); }
}
