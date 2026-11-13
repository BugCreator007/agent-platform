package com.agentplatform.modules.release.infrastructure.persistence;

import com.agentplatform.modules.release.domain.repository.ReleaseRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.demo.seed", matchIfMissing = true)
public class ReleaseDataSeeder implements ApplicationRunner {
    private final ReleaseRepository repository;
    public ReleaseDataSeeder(ReleaseRepository repository) { this.repository = repository; }
    @Override public void run(ApplicationArguments args) {
        if (repository.findAll().isEmpty()) new ReleaseSeedData().findAll().forEach(repository::save);
    }
}
