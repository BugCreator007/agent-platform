package com.agentplatform.modules.workflow.infrastructure.persistence.repository;

import com.agentplatform.modules.workflow.infrastructure.persistence.mapper.WorkflowInstanceMapper;

import com.agentplatform.modules.workflow.infrastructure.persistence.entity.WorkflowInstanceEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 流程实例仓储（Mapper 包装层）。
 *
 * <p>所属层：infrastructure 基础设施层。只负责实体级 CRUD，不感知领域模型；
 * 领域端口适配由 {@link MybatisWorkflowInstanceRepository} 聚合本类完成。</p>
 */
@Repository
public class WorkflowInstanceRepository {
    /** MyBatis-Plus Mapper。 */
    private final WorkflowInstanceMapper mapper;

    /**
     * 构造仓储。
     *
     * @param mapper 流程实例 Mapper
     */
    public WorkflowInstanceRepository(WorkflowInstanceMapper mapper) { this.mapper = mapper; }

    /**
     * 查询全部流程实例实体。
     *
     * @return 实例实体列表
     */
    public List<WorkflowInstanceEntity> findAll() { return mapper.selectList(null); }

    /**
     * 按 ID 查询流程实例实体。
     *
     * @param id 实例 ID
     * @return 实例实体（可能为空）
     */
    public Optional<WorkflowInstanceEntity> findById(String id) { return Optional.ofNullable(mapper.selectById(id)); }

    /**
     * 新增或更新流程实例实体。
     *
     * @param instance 实例实体
     */
    public void save(WorkflowInstanceEntity instance) { mapper.insertOrUpdate(instance); }
}
