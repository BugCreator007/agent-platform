package com.agentplatform.modules.system.infrastructure.persistence;

import com.agentplatform.modules.system.domain.repository.SystemRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.demo.seed", matchIfMissing = true)
public class SystemDataSeeder implements ApplicationRunner {
    private final SystemRepository repository;
    public SystemDataSeeder(SystemRepository repository) { this.repository = repository; }
    @Override public void run(ApplicationArguments args) {
        if (!repository.members().isEmpty()) return;
        SystemSeedData seed = new SystemSeedData();
        seed.members().forEach(repository::saveMember);
        seed.keys().forEach(repository::saveKey);
        seed.audit().forEach(repository::saveAudit);
    }
}
