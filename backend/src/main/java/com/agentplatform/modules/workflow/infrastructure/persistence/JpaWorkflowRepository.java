package com.agentplatform.modules.workflow.infrastructure.persistence;

import com.agentplatform.modules.workflow.domain.model.Workflow;
import com.agentplatform.modules.workflow.domain.repository.WorkflowRepository;
import com.agentplatform.modules.workflow.infrastructure.persistence.entity.WorkflowEntity;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Primary
@Repository
public class JpaWorkflowRepository implements WorkflowRepository {
    private final WorkflowJpaRepository jpa;
    public JpaWorkflowRepository(WorkflowJpaRepository jpa) { this.jpa = jpa; }
    @Override public List<Workflow> findAll() { return jpa.findAll().stream().map(WorkflowEntity::toDomain).toList(); }
    @Override public Optional<Workflow> findById(String id) { return jpa.findById(id).map(WorkflowEntity::toDomain); }
    @Override public void save(Workflow workflow) { jpa.save(WorkflowEntity.from(workflow)); }
}
