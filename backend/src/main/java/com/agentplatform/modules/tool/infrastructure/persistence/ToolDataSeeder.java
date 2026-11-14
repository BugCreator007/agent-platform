package com.agentplatform.modules.tool.infrastructure.persistence;

import com.agentplatform.modules.tool.domain.repository.ToolRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.demo.seed", matchIfMissing = true)
public class ToolDataSeeder implements ApplicationRunner {
    private final ToolRepository repository;
    public ToolDataSeeder(ToolRepository repository) { this.repository = repository; }
    @Override public void run(ApplicationArguments args) {
        ToolSeedData seed = new ToolSeedData();
        if (repository.findAll().isEmpty()) {
            seed.findAll().forEach(repository::save);
        } else {
            seed.findByName("weather.query")
                    .filter(tool -> repository.findByName(tool.name()).isEmpty())
                    .ifPresent(repository::save);
        }
    }
}
