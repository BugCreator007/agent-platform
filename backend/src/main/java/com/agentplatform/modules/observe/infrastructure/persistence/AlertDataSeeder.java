package com.agentplatform.modules.observe.infrastructure.persistence;

import com.agentplatform.modules.observe.domain.repository.AlertRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.demo.seed", matchIfMissing = true)
public class AlertDataSeeder implements ApplicationRunner {
    private final AlertRepository repository;
    public AlertDataSeeder(AlertRepository repository) { this.repository = repository; }
    @Override public void run(ApplicationArguments args) {
        if (repository.findAll().isEmpty()) new AlertSeedData().findAll().forEach(repository::save);
    }
}
