package com.agentplatform.modules.model.infrastructure.persistence;

import com.agentplatform.modules.model.domain.repository.ModelRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.demo.seed", matchIfMissing = true)
public class ModelDataSeeder implements ApplicationRunner {
    private final ModelRepository repository;
    public ModelDataSeeder(ModelRepository repository) { this.repository = repository; }
    @Override public void run(ApplicationArguments args) {
        if (repository.findProviders().isEmpty()) {
            ModelSeedData seed = new ModelSeedData();
            seed.findProviders().forEach(repository::saveProvider);
            seed.findModels().forEach(repository::saveModel);
            repository.saveRouting(seed.routing());
        }
    }
}
