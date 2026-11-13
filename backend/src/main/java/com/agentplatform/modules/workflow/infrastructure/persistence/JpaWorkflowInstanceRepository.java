package com.agentplatform.modules.workflow.infrastructure.persistence;

import com.agentplatform.modules.workflow.domain.model.WorkflowInstance;
import com.agentplatform.modules.workflow.domain.model.WorkflowNodeRun;
import com.agentplatform.modules.workflow.domain.repository.WorkflowInstanceRepository;
import com.agentplatform.modules.workflow.infrastructure.persistence.entity.WorkflowInstanceEntity;
import com.agentplatform.modules.workflow.infrastructure.persistence.entity.WorkflowNodeRunEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JpaWorkflowInstanceRepository implements WorkflowInstanceRepository {
    private final WorkflowInstanceJpaRepository instances;
    private final WorkflowNodeRunJpaRepository nodeRuns;
    public JpaWorkflowInstanceRepository(WorkflowInstanceJpaRepository instances, WorkflowNodeRunJpaRepository nodeRuns) {
        this.instances = instances; this.nodeRuns = nodeRuns;
    }
    @Override public WorkflowInstance save(WorkflowInstance instance) { return instances.save(WorkflowInstanceEntity.from(instance)).toDomain(); }
    @Override public WorkflowNodeRun saveNodeRun(WorkflowNodeRun nodeRun) { return nodeRuns.save(WorkflowNodeRunEntity.from(nodeRun)).toDomain(); }
    @Override public Optional<WorkflowInstance> findById(String id) { return instances.findById(id).map(WorkflowInstanceEntity::toDomain); }
    @Override public List<WorkflowInstance> findAll() { return instances.findAll().stream().map(WorkflowInstanceEntity::toDomain).toList(); }
    @Override public List<WorkflowNodeRun> findNodeRuns(String instanceId) { return nodeRuns.findByInstanceIdOrderByStartedAtAsc(instanceId).stream().map(WorkflowNodeRunEntity::toDomain).toList(); }
}
