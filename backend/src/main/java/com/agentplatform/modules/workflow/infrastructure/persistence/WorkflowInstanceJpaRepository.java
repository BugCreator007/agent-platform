package com.agentplatform.modules.workflow.infrastructure.persistence;

import com.agentplatform.modules.workflow.infrastructure.persistence.entity.WorkflowInstanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowInstanceJpaRepository extends JpaRepository<WorkflowInstanceEntity, String> {
}
