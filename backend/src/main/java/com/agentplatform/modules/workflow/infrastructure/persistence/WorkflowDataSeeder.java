package com.agentplatform.modules.workflow.infrastructure.persistence;

import com.agentplatform.modules.workflow.domain.repository.WorkflowRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.demo.seed", matchIfMissing = true)
public class WorkflowDataSeeder implements ApplicationRunner {
    private final WorkflowRepository repository;
    public WorkflowDataSeeder(WorkflowRepository repository) { this.repository = repository; }
    @Override public void run(ApplicationArguments args) {
        if (repository.findAll().isEmpty()) new WorkflowSeedData().findAll().forEach(repository::save);
    }
}
