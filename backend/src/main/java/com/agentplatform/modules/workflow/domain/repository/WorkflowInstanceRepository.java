package com.agentplatform.modules.workflow.domain.repository;

import com.agentplatform.modules.workflow.domain.model.WorkflowInstance;
import com.agentplatform.modules.workflow.domain.model.WorkflowNodeRun;

import java.util.List;
import java.util.Optional;

public interface WorkflowInstanceRepository {
    WorkflowInstance save(WorkflowInstance instance);
    WorkflowNodeRun saveNodeRun(WorkflowNodeRun nodeRun);
    Optional<WorkflowInstance> findById(String id);
    List<WorkflowInstance> findAll();
    List<WorkflowNodeRun> findNodeRuns(String instanceId);
}
