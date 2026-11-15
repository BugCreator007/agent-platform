package com.agentplatform.modules.workflow.infrastructure.persistence.repository;

import com.agentplatform.modules.workflow.infrastructure.persistence.mapper.WorkflowNodeRunMapper;

import com.agentplatform.modules.workflow.infrastructure.persistence.entity.WorkflowNodeRunEntity;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 节点运行记录仓储（Mapper 包装层）。
 *
 * <p>所属层：infrastructure 基础设施层。只负责实体级读写，不感知领域模型；
 * 领域端口适配由 {@link MybatisWorkflowInstanceRepository} 聚合本类完成。</p>
 */
@Repository
public class WorkflowNodeRunRepository {
    /** MyBatis-Plus Mapper。 */
    private final WorkflowNodeRunMapper mapper;

    /**
     * 构造仓储。
     *
     * @param mapper 节点运行记录 Mapper
     */
    public WorkflowNodeRunRepository(WorkflowNodeRunMapper mapper) { this.mapper = mapper; }

    /**
     * 查询某实例的全部节点运行记录实体，按开始时间升序。
     *
     * @param instanceId 流程实例 ID
     * @return 节点运行记录实体列表
     */
    public List<WorkflowNodeRunEntity> findByInstance(String instanceId) { return mapper.findByInstanceIdOrderByStartedAtAsc(instanceId); }

    /**
     * 新增或更新节点运行记录实体。
     *
     * @param nodeRun 节点运行记录实体
     */
    public void save(WorkflowNodeRunEntity nodeRun) { mapper.insertOrUpdate(nodeRun); }
}
