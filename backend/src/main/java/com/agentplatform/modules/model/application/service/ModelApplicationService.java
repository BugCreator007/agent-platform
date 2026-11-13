package com.agentplatform.modules.model.application.service;

import com.agentplatform.common.error.BusinessException;
import com.agentplatform.common.error.ErrorCode;
import com.agentplatform.modules.model.application.query.ProviderTestResult;
import com.agentplatform.modules.model.domain.model.ModelInfo;
import com.agentplatform.modules.model.domain.model.Provider;
import com.agentplatform.modules.model.domain.model.RoutingPolicy;
import com.agentplatform.modules.model.domain.repository.ModelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ModelApplicationService {
    private final ModelRepository repository;
    private final ProviderProbeService probe;

    public ModelApplicationService(ModelRepository repository, ProviderProbeService probe) {
        this.repository = repository;
        this.probe = probe;
    }

    public List<Provider> providers() { return repository.findProviders(); }
    public List<ModelInfo> models(String providerId) {
        return repository.findModels().stream()
                .filter(m -> providerId == null || providerId.isBlank() || m.providerId().equals(providerId))
                .toList();
    }
    public RoutingPolicy routing() { return repository.routing(); }

    public ProviderTestResult test(String id) {
        return probe.test(requireProvider(id));
    }

    public Provider updateKey(String id, String key) {
        if (key == null || key.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "API Key 不能为空");
        }
        Provider provider = requireProvider(id);
        provider.updateKey(key);
        repository.saveProvider(provider);
        return provider;
    }

    public Provider toggle(String id) {
        Provider provider = requireProvider(id);
        provider.toggle();
        repository.saveProvider(provider);
        return provider;
    }

    public Provider create(String name, String endpoint, String region, String key, double quota, String protocol) {
        if (name == null || name.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "厂商名称不能为空");
        }
        String id = "provider-" + UUID.randomUUID().toString().substring(0, 8);
        Provider provider = new Provider(id, name, "healthy",
                endpoint == null || endpoint.isBlank() ? "https://api.example.com/v1" : endpoint,
                0, 0, 0, "—", 100, quota <= 0 ? 10000 : quota, 0,
                "未配置", key, protocol, region == null || region.isBlank() ? "华东 1" : region, "备用");
        repository.saveProvider(provider);
        return provider;
    }

    public Provider updateQuota(String id, double quota) {
        Provider provider = requireProvider(id);
        provider.updateQuota(quota);
        repository.saveProvider(provider);
        return provider;
    }

    @Transactional
    public void deleteProvider(String id) {
        requireProvider(id);
        repository.deleteModelsByProvider(id);
        repository.deleteProvider(id);
    }

    public ModelInfo createModel(String modelId, String providerId, String name, String context,
                                 double inPrice, double outPrice, String upstreamModel,
                                 List<String> capabilities, int routeWeight, String status) {
        if (providerId == null || providerId.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "厂商不能为空");
        }
        requireProvider(providerId);
        String id = modelId == null || modelId.isBlank()
                ? "model-" + UUID.randomUUID().toString().substring(0, 8) : modelId.trim();
        if (repository.findModel(id).isPresent()) {
            throw new BusinessException(ErrorCode.NAME_CONFLICT, "模型 ID 已存在: " + id);
        }
        ModelInfo model = new ModelInfo(id, providerId,
                name == null || name.isBlank() ? id : name,
                context == null || context.isBlank() ? "128K" : context,
                Math.max(0, inPrice), Math.max(0, outPrice),
                capabilities == null || capabilities.isEmpty() ? List.of("文本") : List.copyOf(capabilities),
                0, "—", status == null || status.isBlank() ? "active" : status,
                Math.max(0, routeWeight), false,
                upstreamModel == null || upstreamModel.isBlank() ? id : upstreamModel);
        repository.saveModel(model);
        return model;
    }

    @Transactional
    public void deleteModel(String id) {
        repository.findModel(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "模型不存在: " + id));
        repository.deleteModel(id);
    }

    @Transactional
    public RoutingPolicy updateRouting(RoutingPolicy policy) {
        if (policy.primary() != null && !policy.primary().isBlank()) {
            repository.findModel(policy.primary())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "主模型不存在: " + policy.primary()));
        }
        List<String> fallback = policy.fallback() == null ? List.of() : policy.fallback().stream()
                .filter(id -> repository.findModel(id).isPresent())
                .distinct()
                .toList();
        RoutingPolicy next = new RoutingPolicy(
                policy.strategy() == null ? "failover" : policy.strategy(),
                policy.primary() == null ? routing().primary() : policy.primary(),
                fallback,
                policy.region() == null ? "华东 1" : policy.region());
        repository.saveRouting(next);
        markDefault(next.primary());
        return next;
    }

    @Transactional
    public ModelInfo setDefault(String modelId) {
        ModelInfo target = repository.findModel(modelId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "模型不存在: " + modelId));
        if ("paused".equals(target.status())) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "暂停的模型不能设为默认");
        }
        markDefault(modelId);
        RoutingPolicy current = routing();
        repository.saveRouting(new RoutingPolicy(current.strategy(), modelId,
                current.fallback(), current.region()));
        target.setDefault(true);
        return target;
    }

    private void markDefault(String modelId) {
        if (modelId == null || modelId.isBlank()) return;
        for (ModelInfo model : repository.findModels()) {
            boolean shouldDefault = model.id().equals(modelId);
            if (model.isDefault() != shouldDefault) {
                model.setDefault(shouldDefault);
                repository.saveModel(model);
            }
        }
    }

    private Provider requireProvider(String id) {
        return repository.findProvider(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "厂商不存在: " + id));
    }
}
