package com.agentplatform.modules.knowledge.infrastructure.persistence;

import com.agentplatform.modules.knowledge.domain.repository.KnowledgeRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(10)
@ConditionalOnProperty(name = "app.demo.seed", matchIfMissing = true)
public class KnowledgeDataSeeder implements ApplicationRunner {
    private final KnowledgeRepository repository;
    public KnowledgeDataSeeder(KnowledgeRepository repository) { this.repository = repository; }
    @Override public void run(ApplicationArguments args) {
        if (!repository.findAllDatasets().isEmpty()) return;
        KnowledgeSeedData seed = new KnowledgeSeedData();
        seed.findAllDatasets().forEach(repository::saveDataset);
        seed.findAllDatasets().forEach(ds -> seed.findDocuments(ds.id()).forEach(repository::saveDocument));
    }
}
