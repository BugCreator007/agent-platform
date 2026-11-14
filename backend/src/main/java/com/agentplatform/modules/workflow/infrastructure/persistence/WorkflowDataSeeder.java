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
        WorkflowSeedData seed = new WorkflowSeedData();
        if (repository.findAll().isEmpty()) {
            seed.findAll().forEach(repository::save);
        } else {
            seed.findById("wf-weather")
                    .filter(workflow -> repository.findById(workflow.id()).isEmpty())
                    .ifPresent(repository::save);
        }
    }
}
