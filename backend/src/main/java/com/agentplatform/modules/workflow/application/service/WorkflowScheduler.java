package com.agentplatform.modules.workflow.application.service;

import com.agentplatform.modules.workflow.domain.model.Workflow;
import com.agentplatform.modules.workflow.domain.model.WorkflowInstance;
import com.agentplatform.modules.workflow.domain.repository.WorkflowInstanceRepository;
import com.agentplatform.modules.workflow.domain.repository.WorkflowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@ConditionalOnProperty(name = "app.workflow.scheduler.enabled", havingValue = "true")
public class WorkflowScheduler {
    private static final Logger log = LoggerFactory.getLogger(WorkflowScheduler.class);
    private final WorkflowRepository workflows;
    private final WorkflowInstanceRepository instances;
    private final WorkflowExecutionService execution;

    public WorkflowScheduler(WorkflowRepository workflows, WorkflowInstanceRepository instances,
                             WorkflowExecutionService execution) {
        this.workflows = workflows; this.instances = instances; this.execution = execution;
    }

    @Scheduled(fixedDelayString = "${app.workflow.scheduler.interval-ms:60000}")
    public void tick() {
        Set<String> active = Set.copyOf(instances.findAll().stream()
                .filter(i -> Set.of("RUNNING", "WAITING_APPROVAL", "WAITING_TIMER", "WAITING_SUBFLOW").contains(i.status()))
                .map(WorkflowInstance::workflowId).toList());
        for (Workflow workflow : workflows.findAll()) {
            String trigger = workflow.trigger() == null ? "" : workflow.trigger();
            if (trigger.contains("定时") || trigger.contains("每天") || trigger.contains("每周")) {
                if (!active.contains(workflow.id())) {
                    log.info("scheduled workflow start: {}", workflow.name());
                    execution.start(workflow.id(), "{\"trigger\":\"scheduled\"}");
                }
            }
        }
    }
}
