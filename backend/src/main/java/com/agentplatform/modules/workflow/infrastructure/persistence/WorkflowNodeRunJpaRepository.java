package com.agentplatform.modules.workflow.infrastructure.persistence;

import com.agentplatform.modules.workflow.infrastructure.persistence.entity.WorkflowNodeRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowNodeRunJpaRepository extends JpaRepository<WorkflowNodeRunEntity, String> {
    List<WorkflowNodeRunEntity> findByInstanceIdOrderByStartedAtAsc(String instanceId);
}
