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

/**
 * 模型目录查询服务（应用层）。
 *
 * <p>供 Agent 运行时与运营概览读取“可调用模型路由”。它把路由策略
 * （主模型 + 回退链）与厂商/模型元数据组装成 {@link ModelRoute} 候选列表，
 * 并可按策略（failover/cost/latency/quality）排序。本类只读，不修改任何持久化状态。</p>
 */
@Service
public class ModelCatalog {
    private final ModelRepository repository;
    private final ProviderCredentialResolver credentials;

    /**
     * 构造模型目录。
     *
     * @param repository  模型模块领域仓储端口
     * @param credentials 厂商密钥解析器（含环境变量回退）
     */
    public ModelCatalog(ModelRepository repository, ProviderCredentialResolver credentials) {
        this.repository = repository;
        this.credentials = credentials;
    }

    /**
     * 计算一次 Agent 调用的候选路由：显式绑定的模型优先，其后拼接配置的回退链。
     * 不可用的厂商仍保留在列表中，以便运行时给出有意义的错误信息。
     *
     * <p>Candidate routes for one agent call: the explicitly bound model first, then the configured
     * fallback chain. Unavailable providers stay in the list so the runtime can report a useful error.</p>
     *
     * @param providerId 智能体绑定的厂商 ID，可为空
     * @param modelId    智能体绑定的模型 ID，可为空
     * @return 按优先级排列的候选路由（保持配置顺序，不应用路由策略排序）
     */
    public List<ModelRoute> routes(String providerId, String modelId) {
        RoutingPolicy policy = repository.routing();
        List<ModelInfo> candidates = new ArrayList<>();
        ModelInfo primary = resolveModel(providerId, modelId, policy);
        if (primary != null) candidates.add(primary);
        // 绑定信息解析不到主模型时，退回策略中配置的主模型
        if (primary == null && policy.primary() != null) {
            repository.findModel(policy.primary()).ifPresent(candidates::add);
        }
        for (String fallbackId : policy.fallback() == null ? List.<String>of() : policy.fallback()) {
            repository.findModel(fallbackId).ifPresent(candidates::add);
        }
        // 仍未命中任何模型时，用默认模型或目录中第一个模型兜底，避免空路由
        if (candidates.isEmpty()) {
            ModelInfo defaultModel = repository.findModels().stream()
                    .filter(ModelInfo::isDefault).findFirst()
                    .orElseGet(() -> repository.findModels().stream().findFirst().orElse(null));
            if (defaultModel != null) candidates.add(defaultModel);
        }
        return toRoutes(candidates, false);
    }

    /**
     * 计算默认路由候选（忽略智能体绑定，仅遵循全局路由策略）。
     * 与 {@link #routes(String, String)} 不同，本方法会按策略对候选排序。
     *
     * @return 按路由策略排序后的候选路由
     */
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

    /**
     * 返回目录中全部模型的候选路由（不做策略排序）。
     *
     * @return 全部可用元数据对应的路由列表，缺少厂商的模型会被跳过
     */
    public List<ModelRoute> allRoutes() {
        return toRoutes(repository.findModels(), false);
    }

    /**
     * 按优先级解析主模型：模型 ID → 厂商下首个未暂停模型 → 策略主模型 → 默认模型。
     */
    private ModelInfo resolveModel(String providerId, String modelId, RoutingPolicy policy) {
        if (modelId != null && !modelId.isBlank()) {
            ModelInfo byId = repository.findModel(modelId).orElse(null);
            if (byId != null) return byId;
        }
        if (providerId != null && !providerId.isBlank()) {
            // 优先选该厂商下未暂停的模型；若全部暂停，则退而取任意一个以便上层报错
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

    /**
     * 把模型元数据转换为路由 DTO，并按需应用路由策略排序。
     *
     * @param models        候选模型
     * @param applyStrategy 是否按全局策略排序（false 时保持配置顺序）
     * @return 去重后的不可变路由列表
     */
    private List<ModelRoute> toRoutes(List<ModelInfo> models, boolean applyStrategy) {
        // LinkedHashMap 去重的同时保留主模型/回退链的原始顺序
        Map<String, ModelRoute> routes = new LinkedHashMap<>();
        for (ModelInfo model : models) {
            ModelRoute route = toRoute(model);
            if (route != null) routes.putIfAbsent(route.modelId(), route);
        }
        List<ModelRoute> result = new ArrayList<>(routes.values());
        if (applyStrategy) {
            String strategy = repository.routing().strategy() == null ? "failover" : repository.routing().strategy();
            switch (strategy) {
                // 成本优先：输入+输出单价之和升序（价格低者先试）
                case "cost" -> result.sort(Comparator.comparingDouble(r -> r.inPrice() + r.outPrice()));
                // 延迟优先：探测延迟升序
                case "latency" -> result.sort(Comparator.comparingLong(ModelRoute::latencyMs));
                // 质量优先：路由权重降序
                case "quality" -> result.sort(Comparator.comparingInt(ModelRoute::routeWeight).reversed());
                default -> { /* failover keeps the configured primary/fallback order */ }
            }
        }
        return List.copyOf(result);
    }

    /**
     * 单个模型的元数据 + 厂商连接信息组装为路由；厂商不存在时返回 null 由调用方跳过。
     */
    private ModelRoute toRoute(ModelInfo model) {
        Provider provider = repository.findProvider(model.providerId()).orElse(null);
        if (provider == null) return null;
        return new ModelRoute(
                provider.id(), provider.name(),
                // 未声明协议时默认按 OpenAI 兼容协议调用
                provider.protocol() == null || provider.protocol().isBlank() ? "openai" : provider.protocol(),
                provider.endpoint(), credentials.resolve(provider),
                model.id(), model.name(), model.upstreamModel(),
                provider.status(), model.status(),
                model.inPrice(), model.outPrice(), model.routeWeight(), latencyMs(model.latency()));
    }

    /**
     * 把 “620 ms”“1,860 ms” 之类的展示文本解析为毫秒数；解析失败返回最大值使其排到最后。
     */
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
