package com.agentplatform.modules.workflow.application.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Always-on recovery loop for persisted timers and parent/child subflows.
 * Unlike the optional cron-like trigger scheduler, this must run in every deployment.
 */
@Component
public class WorkflowResumeScheduler {
    private final WorkflowExecutionService execution;

    public WorkflowResumeScheduler(WorkflowExecutionService execution) {
        this.execution = execution;
    }

    @Scheduled(fixedDelayString = "${app.workflow.resume-interval-ms:5000}")
    public void resume() {
        execution.resumeTimers();
        execution.resumeSubflows();
    }
}
