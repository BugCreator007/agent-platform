package com.agentplatform.modules.chat.infrastructure.persistence;

import com.agentplatform.modules.chat.domain.repository.SessionRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.demo.seed", matchIfMissing = true)
public class ChatDataSeeder implements ApplicationRunner {
    private final SessionRepository repository;
    public ChatDataSeeder(SessionRepository repository) { this.repository = repository; }
    @Override public void run(ApplicationArguments args) {
        if (repository.findAll().isEmpty()) new SessionSeedData().findAll().forEach(repository::save);
    }
}
