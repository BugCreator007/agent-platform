package com.agentplatform.modules.model.infrastructure.persistence;

import com.agentplatform.framework.security.SecretCipher;
import com.agentplatform.modules.model.domain.model.ModelInfo;
import com.agentplatform.modules.model.domain.model.Provider;
import com.agentplatform.modules.model.domain.model.RoutingPolicy;
import com.agentplatform.modules.model.domain.repository.ModelRepository;
import com.agentplatform.modules.model.infrastructure.persistence.entity.ModelEntity;
import com.agentplatform.modules.model.infrastructure.persistence.entity.ProviderEntity;
import com.agentplatform.modules.model.infrastructure.persistence.entity.RoutingPolicyEntity;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Primary
@Repository
public class JpaModelRepository implements ModelRepository {
    private final ProviderJpaRepository providers;
    private final ModelJpaRepository models;
    private final RoutingJpaRepository routing;
    private final SecretCipher cipher;

    public JpaModelRepository(ProviderJpaRepository providers, ModelJpaRepository models,
                              RoutingJpaRepository routing, SecretCipher cipher) {
        this.providers = providers; this.models = models; this.routing = routing; this.cipher = cipher;
    }

    @Override public List<Provider> findProviders() { return providers.findAll().stream().map(p -> p.toDomain(cipher)).toList(); }
    @Override public Optional<Provider> findProvider(String id) { return providers.findById(id).map(p -> p.toDomain(cipher)); }
    @Override public List<ModelInfo> findModels() { return models.findAll().stream().map(ModelEntity::toDomain).toList(); }
    @Override public Optional<ModelInfo> findModel(String id) { return models.findById(id).map(ModelEntity::toDomain); }
    @Override public void saveProvider(Provider provider) { providers.save(ProviderEntity.from(provider, cipher)); }
    @Override public void saveModel(ModelInfo model) { models.save(ModelEntity.from(model)); }
    @Override public void deleteModel(String id) { models.deleteById(id); }
    @Override public RoutingPolicy routing() {
        return routing.findById("default").map(RoutingPolicyEntity::toDomain)
                .orElse(new RoutingPolicy("质量优先", "claude-sonnet-5-5", List.of(), "华东 1"));
    }
    @Override public void saveRouting(RoutingPolicy policy) { routing.save(RoutingPolicyEntity.from(policy)); }
    @Override public void deleteProvider(String id) { providers.deleteById(id); }
    @Override public void deleteModelsByProvider(String providerId) { models.deleteByProviderId(providerId); }
}
