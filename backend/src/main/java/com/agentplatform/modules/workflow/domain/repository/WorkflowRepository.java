package com.agentplatform.modules.workflow.domain.repository;

import com.agentplatform.modules.workflow.domain.model.Workflow;

import java.util.List;
import java.util.Optional;

public interface WorkflowRepository {
    List<Workflow> findAll();
    Optional<Workflow> findById(String id);
    void save(Workflow workflow);
}
