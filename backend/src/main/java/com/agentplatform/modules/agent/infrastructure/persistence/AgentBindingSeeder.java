package com.agentplatform.modules.agent.infrastructure.persistence;

import com.agentplatform.modules.agent.domain.repository.AgentRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(30)
@ConditionalOnProperty(name = "app.demo.seed", matchIfMissing = true)
public class AgentBindingSeeder implements ApplicationRunner {
    private final AgentRepository repository;
    public AgentBindingSeeder(AgentRepository repository) { this.repository = repository; }
    @Override public void run(ApplicationArguments args) {
        repository.findAll().forEach(agent -> {
            if (agent.providerId() == null) {
                AgentModelBinder.bind(agent);
                if (agent.providerId() != null) repository.save(agent);
            }
        });
    }
}
