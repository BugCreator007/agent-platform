package com.agentplatform.modules.workflow.infrastructure.persistence;

import com.agentplatform.modules.workflow.infrastructure.persistence.entity.WorkflowEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowJpaRepository extends JpaRepository<WorkflowEntity, String> {
}
