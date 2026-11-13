package com.agentplatform.modules.model.infrastructure.persistence;

import com.agentplatform.modules.model.domain.repository.ModelRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
@ConditionalOnProperty(name = "app.demo.seed", matchIfMissing = true)
public class ModelDataSeeder implements ApplicationRunner {
    private static final Set<String> INCREMENTAL_MODELS = Set.of(
            "qwen3-coder-plus", "qwen-vl-max", "kimi-k2.5", "kimi-k2-turbo", "moonshot-v1-128k");
    private final ModelRepository repository;
    private final String deepSeekKey;

    public ModelDataSeeder(ModelRepository repository,
                           @Value("${app.ai.deepseek.api-key:}") String deepSeekKey) {
        this.repository = repository;
        this.deepSeekKey = deepSeekKey == null ? "" : deepSeekKey.trim();
    }

    @Override public void run(ApplicationArguments args) {
        ModelSeedData seed = new ModelSeedData();
        if (repository.findProviders().isEmpty()) {
            seed.findProviders().forEach(repository::saveProvider);
            seed.findModels().forEach(repository::saveModel);
            repository.saveRouting(seed.routing());
        } else {
            // Add newly introduced providers/models without resurrecting user-deleted catalog entries.
            seed.findProvider("kimi").filter(p -> repository.findProvider(p.id()).isEmpty())
                    .ifPresent(repository::saveProvider);
            seed.findModels().stream()
                    .filter(m -> INCREMENTAL_MODELS.contains(m.id()))
                    .filter(m -> repository.findModel(m.id()).isEmpty())
                    .forEach(repository::saveModel);
        }
        // Existing local installs keep working from .env until an explicit key is stored in the console.
        repository.findProvider("deepseek").ifPresent(provider -> {
            if (!provider.keyConfigured() && !deepSeekKey.isBlank()) {
                provider.updateKey(deepSeekKey);
                repository.saveProvider(provider);
            }
        });
    }
}
