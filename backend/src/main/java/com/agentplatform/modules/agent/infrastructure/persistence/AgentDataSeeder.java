package com.agentplatform.modules.agent.infrastructure.persistence;

import com.agentplatform.modules.agent.domain.repository.AgentRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.demo.seed", matchIfMissing = true)
public class AgentDataSeeder implements ApplicationRunner {
    private final AgentRepository repository;
    public AgentDataSeeder(AgentRepository repository) { this.repository = repository; }
    @Override public void run(ApplicationArguments args) {
        if (repository.findAll().isEmpty()) new AgentSeedData().findAll().forEach(repository::save);
    }
}
