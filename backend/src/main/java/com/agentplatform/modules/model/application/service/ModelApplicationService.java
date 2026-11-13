package com.agentplatform.modules.model.application.service;

import com.agentplatform.common.error.BusinessException;
import com.agentplatform.common.error.ErrorCode;
import com.agentplatform.modules.model.domain.model.ModelInfo;
import com.agentplatform.modules.model.domain.model.Provider;
import com.agentplatform.modules.model.domain.model.RoutingPolicy;
import com.agentplatform.modules.model.domain.repository.ModelRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ModelApplicationService {
    private final ModelRepository repository;

    public ModelApplicationService(ModelRepository repository) {
        this.repository = repository;
    }

    public List<Provider> providers() { return repository.findProviders(); }
    public List<ModelInfo> models(String providerId) {
        return repository.findModels().stream()
                .filter(m -> providerId == null || providerId.isBlank() || m.providerId().equals(providerId))
                .toList();
    }
    public RoutingPolicy routing() { return repository.routing(); }

    public Provider test(String id) {
        Provider provider = requireProvider(id);
        provider.markTested((280 + (int) (Math.random() * 500)) + " ms");
        repository.saveProvider(provider);
        return provider;
    }

    public Provider rotateKey(String id) {
        Provider provider = requireProvider(id);
        provider.rotateKey("sk-" + provider.id() + "-••••••••" + UUID.randomUUID().toString().substring(0, 4));
        repository.saveProvider(provider);
        return provider;
    }

    public Provider toggle(String id) {
        Provider provider = requireProvider(id);
        provider.toggle();
        repository.saveProvider(provider);
        return provider;
    }

    public Provider create(String name, String endpoint, String region, String key, double quota) {
        if (name == null || name.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "厂商名称不能为空");
        }
        String id = "provider-" + UUID.randomUUID().toString().substring(0, 8);
        Provider provider = new Provider(id, name, "healthy",
                endpoint == null || endpoint.isBlank() ? "https://api.example.com/v1" : endpoint,
                0, 0, 0, "—", 100, quota <= 0 ? 10000 : quota, 0,
                key == null || key.isBlank() ? "sk-••••••••" : key,
                region == null || region.isBlank() ? "华东 1" : region, "备用");
        repository.saveProvider(provider);
        return provider;
    }

    public Provider updateQuota(String id, double quota) {
        Provider provider = requireProvider(id);
        provider.updateQuota(quota);
        repository.saveProvider(provider);
        return provider;
    }

    public void deleteProvider(String id) {
        requireProvider(id);
        repository.deleteModelsByProvider(id);
        repository.deleteProvider(id);
    }

    public RoutingPolicy updateRouting(RoutingPolicy policy) {
        RoutingPolicy next = new RoutingPolicy(
                policy.strategy() == null ? "failover" : policy.strategy(),
                policy.primary() == null ? routing().primary() : policy.primary(),
                policy.fallback() == null ? List.of() : List.copyOf(policy.fallback()),
                policy.region() == null ? "华东 1" : policy.region());
        repository.saveRouting(next);
        return next;
    }

    public ModelInfo setDefault(String modelId) {
        ModelInfo target = repository.findModel(modelId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "模型不存在: " + modelId));
        if ("paused".equals(target.status())) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "暂停的模型不能设为默认");
        }
        repository.findModels().forEach(m -> m.setDefault(m.id().equals(modelId)));
        repository.saveModel(target);
        return target;
    }

    private Provider requireProvider(String id) {
        return repository.findProvider(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "厂商不存在: " + id));
    }
}
