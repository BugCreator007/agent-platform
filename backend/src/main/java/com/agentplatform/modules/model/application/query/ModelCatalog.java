package com.agentplatform.modules.model.application.query;

import com.agentplatform.modules.model.application.service.ProviderCredentialResolver;
import com.agentplatform.modules.model.domain.model.ModelInfo;
import com.agentplatform.modules.model.domain.model.Provider;
import com.agentplatform.modules.model.domain.model.RoutingPolicy;
import com.agentplatform.modules.model.domain.repository.ModelRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ModelCatalog {
    private final ModelRepository repository;
    private final ProviderCredentialResolver credentials;

    public ModelCatalog(ModelRepository repository, ProviderCredentialResolver credentials) {
        this.repository = repository;
        this.credentials = credentials;
    }

    /**
     * Candidate routes for one agent call: the explicitly bound model first, then the configured
     * fallback chain. Unavailable providers stay in the list so the runtime can report a useful error.
     */
    public List<ModelRoute> routes(String providerId, String modelId) {
        RoutingPolicy policy = repository.routing();
        List<ModelInfo> candidates = new ArrayList<>();
        ModelInfo primary = resolveModel(providerId, modelId, policy);
        if (primary != null) candidates.add(primary);
        if (primary == null && policy.primary() != null) {
            repository.findModel(policy.primary()).ifPresent(candidates::add);
        }
        for (String fallbackId : policy.fallback() == null ? List.<String>of() : policy.fallback()) {
            repository.findModel(fallbackId).ifPresent(candidates::add);
        }
        if (candidates.isEmpty()) {
            ModelInfo defaultModel = repository.findModels().stream()
                    .filter(ModelInfo::isDefault).findFirst()
                    .orElseGet(() -> repository.findModels().stream().findFirst().orElse(null));
            if (defaultModel != null) candidates.add(defaultModel);
        }
        return toRoutes(candidates, false);
    }

    public List<ModelRoute> defaultRoutes() {
        RoutingPolicy policy = repository.routing();
        List<ModelInfo> candidates = new ArrayList<>();
        if (policy.primary() != null) repository.findModel(policy.primary()).ifPresent(candidates::add);
        for (String fallbackId : policy.fallback() == null ? List.<String>of() : policy.fallback()) {
            repository.findModel(fallbackId).ifPresent(candidates::add);
        }
        if (candidates.isEmpty()) {
            repository.findModels().stream().filter(ModelInfo::isDefault).findFirst()
                    .or(() -> repository.findModels().stream().findFirst())
                    .ifPresent(candidates::add);
        }
        return toRoutes(candidates, true);
    }

    public List<ModelRoute> allRoutes() {
        return toRoutes(repository.findModels(), false);
    }

    private ModelInfo resolveModel(String providerId, String modelId, RoutingPolicy policy) {
        if (modelId != null && !modelId.isBlank()) {
            ModelInfo byId = repository.findModel(modelId).orElse(null);
            if (byId != null) return byId;
        }
        if (providerId != null && !providerId.isBlank()) {
            ModelInfo firstForProvider = repository.findModels().stream()
                    .filter(m -> providerId.equals(m.providerId()))
                    .filter(m -> !"paused".equalsIgnoreCase(m.status()))
                    .findFirst()
                    .orElseGet(() -> repository.findModels().stream()
                            .filter(m -> providerId.equals(m.providerId())).findFirst().orElse(null));
            if (firstForProvider != null) return firstForProvider;
        }
        if (policy.primary() != null) {
            ModelInfo policyModel = repository.findModel(policy.primary()).orElse(null);
            if (policyModel != null) return policyModel;
        }
        return repository.findModels().stream().filter(ModelInfo::isDefault).findFirst().orElse(null);
    }

    private List<ModelRoute> toRoutes(List<ModelInfo> models, boolean applyStrategy) {
        Map<String, ModelRoute> routes = new LinkedHashMap<>();
        for (ModelInfo model : models) {
            ModelRoute route = toRoute(model);
            if (route != null) routes.putIfAbsent(route.modelId(), route);
        }
        List<ModelRoute> result = new ArrayList<>(routes.values());
        if (applyStrategy) {
            String strategy = repository.routing().strategy() == null ? "failover" : repository.routing().strategy();
            switch (strategy) {
                case "cost" -> result.sort(Comparator.comparingDouble(r -> r.inPrice() + r.outPrice()));
                case "latency" -> result.sort(Comparator.comparingLong(ModelRoute::latencyMs));
                case "quality" -> result.sort(Comparator.comparingInt(ModelRoute::routeWeight).reversed());
                default -> { /* failover keeps the configured primary/fallback order */ }
            }
        }
        return List.copyOf(result);
    }

    private ModelRoute toRoute(ModelInfo model) {
        Provider provider = repository.findProvider(model.providerId()).orElse(null);
        if (provider == null) return null;
        return new ModelRoute(
                provider.id(), provider.name(),
                provider.protocol() == null || provider.protocol().isBlank() ? "openai" : provider.protocol(),
                provider.endpoint(), credentials.resolve(provider),
                model.id(), model.name(), model.upstreamModel(),
                provider.status(), model.status(),
                model.inPrice(), model.outPrice(), model.routeWeight(), latencyMs(model.latency()));
    }

    private long latencyMs(String latency) {
        if (latency == null || latency.isBlank()) return Long.MAX_VALUE;
        String digits = latency.replaceAll("[^0-9]", "");
        if (digits.isBlank()) return Long.MAX_VALUE;
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException e) {
            return Long.MAX_VALUE;
        }
    }
}
