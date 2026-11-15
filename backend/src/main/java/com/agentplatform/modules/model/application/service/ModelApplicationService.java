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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 模型模块应用服务（应用层）。
 *
 * <p>编排厂商与模型的增删改查、密钥更新、额度调整、默认模型维护以及路由策略更新。
 * 所有写操作最终通过领域仓储端口落库；密钥在持久化适配器中加密。
 * 删除模型/厂商后会同步清理路由策略，保证主模型与回退链始终指向存在的模型。</p>
 */
@Service
public class ModelApplicationService {
    private final ModelRepository repository;
    private final ProviderProbeService probe;

    /**
     * 构造应用服务。
     *
     * @param repository 模型模块领域仓储端口
     * @param probe      厂商连通性探测服务
     */
    public ModelApplicationService(ModelRepository repository, ProviderProbeService probe) {
        this.repository = repository;
        this.probe = probe;
    }

    /**
     * 查询全部厂商。
     *
     * @return 厂商列表（API Key 已脱敏）
     */
    public List<Provider> providers() { return repository.findProviders(); }

    /**
     * 按厂商过滤查询模型；providerId 为空时返回全部。
     *
     * @param providerId 厂商 ID，可为空
     * @return 匹配的模型列表
     */
    public List<ModelInfo> models(String providerId) {
        return repository.findModels().stream()
                .filter(m -> providerId == null || providerId.isBlank() || m.providerId().equals(providerId))
                .toList();
    }

    /**
     * 查询当前全局路由策略。
     *
     * @return 路由策略（策略类型、主模型、回退链、区域）
     */
    public RoutingPolicy routing() { return repository.routing(); }

    /**
     * 对指定厂商发起连通性探测。
     *
     * @param id 厂商 ID
     * @return 探测结果，同时会更新厂商的状态与延迟
     * @throws BusinessException 厂商不存在时抛出 NOT_FOUND
     */
    public ProviderTestResult test(String id) {
        return probe.test(requireProvider(id));
    }

    /**
     * 更新厂商 API Key（明文只在内存中处理，落库时由适配器解密/加密）。
     *
     * @param id  厂商 ID
     * @param key 新的 API Key，不能为空
     * @return 更新后的厂商（key 字段为脱敏展示值）
     * @throws BusinessException Key 为空时抛出 VALIDATION_ERROR；厂商不存在时抛出 NOT_FOUND
     */
    public Provider updateKey(String id, String key) {
        if (key == null || key.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "API Key 不能为空");
        }
        Provider provider = requireProvider(id);
        provider.updateKey(key);
        repository.saveProvider(provider);
        return provider;
    }

    /**
     * 启用/停用厂商（healthy 与 paused 之间切换）。
     *
     * @param id 厂商 ID
     * @return 切换后的厂商
     * @throws BusinessException 厂商不存在时抛出 NOT_FOUND
     */
    public Provider toggle(String id) {
        Provider provider = requireProvider(id);
        provider.toggle();
        repository.saveProvider(provider);
        return provider;
    }

    /**
     * 新建厂商。ID 由 “provider-” + 随机 8 位组成，未填写的字段使用默认值。
     *
     * @param name     厂商名称，不能为空
     * @param endpoint API 基地址，为空时使用占位地址
     * @param region   区域，为空时默认“华东 1”
     * @param key      API Key，可为空（后续再配置）
     * @param quota    额度，<=0 时默认 10000
     * @param protocol 协议（openai/anthropic）
     * @return 新建的厂商
     * @throws BusinessException 名称为空时抛出 VALIDATION_ERROR
     */
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

    /**
     * 更新厂商额度（负数按 0 处理）。
     *
     * @param id    厂商 ID
     * @param quota 新额度
     * @return 更新后的厂商
     * @throws BusinessException 厂商不存在时抛出 NOT_FOUND
     */
    public Provider updateQuota(String id, double quota) {
        Provider provider = requireProvider(id);
        provider.updateQuota(quota);
        repository.saveProvider(provider);
        return provider;
    }

    /**
     * 删除厂商及其名下全部模型，并修复路由策略。
     *
     * <p>在事务中执行，避免出现“模型已删、路由仍指向”的中间状态。</p>
     *
     * @param id 厂商 ID
     * @throws BusinessException 厂商不存在时抛出 NOT_FOUND
     */
    @Transactional
    public void deleteProvider(String id) {
        requireProvider(id);
        List<ModelInfo> all = repository.findModels();
        Set<String> removed = all.stream()
                .filter(m -> id.equals(m.providerId()))
                .map(ModelInfo::id)
                .collect(Collectors.toSet());
        List<ModelInfo> remaining = all.stream().filter(m -> !removed.contains(m.id())).toList();
        cleanupRouting(removed, remaining);
        repository.deleteModelsByProvider(id);
        repository.deleteProvider(id);
    }

    /**
     * 新建模型。模型 ID 为空时自动生成；价格、权重做非负约束。
     *
     * @param modelId       模型 ID，可为空
     * @param providerId    所属厂商 ID，不能为空且必须存在
     * @param name          模型展示名，为空时用 ID
     * @param context       上下文窗口，如 128K
     * @param inPrice       输入价格（每百万 token）
     * @param outPrice      输出价格（每百万 token）
     * @param upstreamModel 上游真实模型名，为空时用 ID
     * @param capabilities  能力标签，为空时默认“文本”
     * @param routeWeight   路由权重
     * @param status        状态，为空时默认 active
     * @return 新建的模型
     * @throws BusinessException 厂商为空或不存在、模型 ID 冲突时抛出对应错误
     */
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

    /**
     * 删除模型并修复路由策略（事务内执行）。
     *
     * @param id 模型 ID
     * @throws BusinessException 模型不存在时抛出 NOT_FOUND
     */
    @Transactional
    public void deleteModel(String id) {
        ModelInfo target = repository.findModel(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "模型不存在: " + id));
        List<ModelInfo> remaining = repository.findModels().stream()
                .filter(m -> !m.id().equals(target.id())).toList();
        cleanupRouting(Set.of(target.id()), remaining);
        repository.deleteModel(id);
    }

    /**
     * 更新全局路由策略：校验主模型存在、过滤失效的回退项并去重。
     *
     * @param policy 新的路由策略
     * @return 规范化并保存后的策略
     * @throws BusinessException 主模型不存在时抛出 NOT_FOUND
     */
    @Transactional
    public RoutingPolicy updateRouting(RoutingPolicy policy) {
        if (policy.primary() != null && !policy.primary().isBlank()) {
            repository.findModel(policy.primary())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "主模型不存在: " + policy.primary()));
        }
        // 回退链只保留真实存在的模型，并去重，防止策略里残留脏数据
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

    /**
     * 将指定模型设为默认模型，并同步为路由策略的主模型。
     *
     * @param modelId 模型 ID
     * @return 设为默认后的模型
     * @throws BusinessException 模型不存在（NOT_FOUND）或已暂停（STATE_CONFLICT）时抛出
     */
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

    /**
     * 模型被删除后修复路由策略：主模型失效时依次尝试回退链、默认模型、未暂停模型、任意模型。
     *
     * @param removed   被删除的模型 ID 集合
     * @param remaining 仍然存在的模型
     */
    private void cleanupRouting(Set<String> removed, List<ModelInfo> remaining) {
        if (removed.isEmpty()) return;
        RoutingPolicy current = routing();
        String currentPrimary = current.primary();
        boolean primaryStillExists = currentPrimary != null && !removed.contains(currentPrimary)
                && remaining.stream().anyMatch(m -> m.id().equals(currentPrimary));
        // 回退链同时过滤已删除项和 “已不在目录中” 的项
        List<String> survivingFallback = current.fallback() == null ? List.of() : current.fallback().stream()
                .filter(f -> !removed.contains(f))
                .filter(f -> remaining.stream().anyMatch(m -> m.id().equals(f)))
                .distinct()
                .toList();
        // 主模型的兜底顺序：原主模型 → 回退链首个 → 默认模型 → 首个未暂停模型 → 任意模型
        String primary = primaryStillExists ? currentPrimary
                : survivingFallback.stream().findFirst()
                .orElseGet(() -> remaining.stream().filter(ModelInfo::isDefault).map(ModelInfo::id).findFirst()
                        .orElseGet(() -> remaining.stream()
                                .filter(m -> !"paused".equalsIgnoreCase(m.status()))
                                .map(ModelInfo::id)
                                .findFirst()
                                .orElseGet(() -> remaining.stream().map(ModelInfo::id).findFirst().orElse(null))));
        List<String> fallback = survivingFallback.stream().filter(f -> !f.equals(primary)).toList();
        repository.saveRouting(new RoutingPolicy(current.strategy(), primary, fallback, current.region()));
        markDefault(primary, remaining);
    }

    /**
     * 以当前目录为基准，保证全局只有指定模型处于默认状态。
     */
    private void markDefault(String modelId) {
        markDefault(modelId, repository.findModels());
    }

    /**
     * 在给定模型集合中把指定模型标记为唯一默认，并持久化发生变化项。
     *
     * @param modelId 目标默认模型 ID，为空时不做任何处理
     * @param models  待检查的模型集合
     */
    private void markDefault(String modelId, List<ModelInfo> models) {
        if (modelId == null || modelId.isBlank()) return;
        for (ModelInfo model : models) {
            boolean shouldDefault = model.id().equals(modelId);
            if (model.isDefault() != shouldDefault) {
                model.setDefault(shouldDefault);
                repository.saveModel(model);
            }
        }
    }

    /**
     * 按 ID 获取厂商，不存在时抛出业务异常。
     *
     * @param id 厂商 ID
     * @return 厂商
     * @throws BusinessException 厂商不存在时抛出 NOT_FOUND
     */
    private Provider requireProvider(String id) {
        return repository.findProvider(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "厂商不存在: " + id));
    }
}
