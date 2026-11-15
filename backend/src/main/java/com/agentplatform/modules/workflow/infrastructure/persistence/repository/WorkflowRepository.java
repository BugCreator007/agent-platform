package com.agentplatform.modules.workflow.infrastructure.persistence.repository;

import com.agentplatform.modules.workflow.infrastructure.persistence.mapper.WorkflowMapper;

import com.agentplatform.modules.workflow.domain.model.Workflow;
import com.agentplatform.modules.workflow.infrastructure.persistence.entity.WorkflowEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 流程定义仓储适配器。
 *
 * <p>所属层：infrastructure 基础设施层。实现 domain 层
 * {@link com.agentplatform.modules.workflow.domain.repository.WorkflowRepository} 端口，
 * 包装 {@link WorkflowMapper} 并完成实体与领域模型转换。</p>
 */
@Repository
public class WorkflowRepository implements com.agentplatform.modules.workflow.domain.repository.WorkflowRepository {
    /** MyBatis-Plus Mapper。 */
    private final WorkflowMapper mapper;

    /**
     * 构造适配器。
     *
     * @param mapper 流程定义 Mapper
     */
    public WorkflowRepository(WorkflowMapper mapper) { this.mapper = mapper; }

    /**
     * 查询全部流程定义并转换为领域模型。
     *
     * @return 流程定义列表
     */
    @Override public List<Workflow> findAll() { return mapper.selectList(null).stream().map(WorkflowEntity::toDomain).toList(); }

    /**
     * 按 ID 查询流程定义。
     *
     * @param id 流程 ID
     * @return 流程定义（可能为空）
     */
    @Override public Optional<Workflow> findById(String id) { return Optional.ofNullable(mapper.selectById(id)).map(WorkflowEntity::toDomain); }

    /**
     * 新增或更新流程定义（按主键是否存在自动选择 insert/update）。
     *
     * @param workflow 流程定义
     */
    @Override public void save(Workflow workflow) { mapper.insertOrUpdate(WorkflowEntity.from(workflow)); }
}
