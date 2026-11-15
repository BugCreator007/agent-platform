package com.agentplatform.modules.workflow.infrastructure.persistence.repository;

import com.agentplatform.modules.workflow.infrastructure.persistence.mapper.WorkflowNodeRunMapper;

import com.agentplatform.modules.workflow.infrastructure.persistence.entity.WorkflowNodeRunEntity;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class WorkflowNodeRunRepository {
    private final WorkflowNodeRunMapper mapper;

    public WorkflowNodeRunRepository(WorkflowNodeRunMapper mapper) { this.mapper = mapper; }

    public List<WorkflowNodeRunEntity> findByInstance(String instanceId) { return mapper.findByInstanceIdOrderByStartedAtAsc(instanceId); }
    public void save(WorkflowNodeRunEntity nodeRun) { mapper.insertOrUpdate(nodeRun); }
}
