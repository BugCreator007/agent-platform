package com.agentplatform.modules.workflow.infrastructure.persistence.repository;

import com.agentplatform.modules.workflow.domain.model.WorkflowInstance;
import com.agentplatform.modules.workflow.domain.model.WorkflowNodeRun;
import com.agentplatform.modules.workflow.domain.repository.WorkflowInstanceRepository;
import com.agentplatform.modules.workflow.infrastructure.persistence.entity.WorkflowInstanceEntity;
import com.agentplatform.modules.workflow.infrastructure.persistence.entity.WorkflowNodeRunEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MybatisWorkflowInstanceRepository implements WorkflowInstanceRepository {
    private final com.agentplatform.modules.workflow.infrastructure.persistence.repository.WorkflowInstanceRepository instances;
    private final WorkflowNodeRunRepository nodeRuns;
    public MybatisWorkflowInstanceRepository(
            com.agentplatform.modules.workflow.infrastructure.persistence.repository.WorkflowInstanceRepository instances,
            WorkflowNodeRunRepository nodeRuns) {
        this.instances = instances; this.nodeRuns = nodeRuns;
    }
    @Override public WorkflowInstance save(WorkflowInstance instance) {
        WorkflowInstanceEntity entity = WorkflowInstanceEntity.from(instance);
        instances.save(entity);
        return entity.toDomain();
    }
    @Override public WorkflowNodeRun saveNodeRun(WorkflowNodeRun nodeRun) {
        WorkflowNodeRunEntity entity = WorkflowNodeRunEntity.from(nodeRun);
        nodeRuns.save(entity);
        return entity.toDomain();
    }
    @Override public Optional<WorkflowInstance> findById(String id) { return instances.findById(id).map(WorkflowInstanceEntity::toDomain); }
    @Override public List<WorkflowInstance> findAll() { return instances.findAll().stream().map(WorkflowInstanceEntity::toDomain).toList(); }
    @Override public List<WorkflowNodeRun> findNodeRuns(String instanceId) { return nodeRuns.findByInstance(instanceId).stream().map(WorkflowNodeRunEntity::toDomain).toList(); }
}
