package com.agentplatform.modules.workflow.infrastructure.persistence.repository;

import com.agentplatform.modules.workflow.infrastructure.persistence.mapper.WorkflowMapper;

import com.agentplatform.modules.workflow.domain.model.Workflow;
import com.agentplatform.modules.workflow.infrastructure.persistence.entity.WorkflowEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class WorkflowRepository implements com.agentplatform.modules.workflow.domain.repository.WorkflowRepository {
    private final WorkflowMapper mapper;
    public WorkflowRepository(WorkflowMapper mapper) { this.mapper = mapper; }
    @Override public List<Workflow> findAll() { return mapper.selectList(null).stream().map(WorkflowEntity::toDomain).toList(); }
    @Override public Optional<Workflow> findById(String id) { return Optional.ofNullable(mapper.selectById(id)).map(WorkflowEntity::toDomain); }
    @Override public void save(Workflow workflow) { mapper.insertOrUpdate(WorkflowEntity.from(workflow)); }
}
