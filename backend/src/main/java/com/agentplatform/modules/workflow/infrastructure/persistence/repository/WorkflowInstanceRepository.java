package com.agentplatform.modules.workflow.infrastructure.persistence.repository;

import com.agentplatform.modules.workflow.infrastructure.persistence.mapper.WorkflowInstanceMapper;

import com.agentplatform.modules.workflow.infrastructure.persistence.entity.WorkflowInstanceEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class WorkflowInstanceRepository {
    private final WorkflowInstanceMapper mapper;

    public WorkflowInstanceRepository(WorkflowInstanceMapper mapper) { this.mapper = mapper; }

    public List<WorkflowInstanceEntity> findAll() { return mapper.selectList(null); }
    public Optional<WorkflowInstanceEntity> findById(String id) { return Optional.ofNullable(mapper.selectById(id)); }
    public void save(WorkflowInstanceEntity instance) { mapper.insertOrUpdate(instance); }
}
