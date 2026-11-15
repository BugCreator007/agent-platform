package com.agentplatform.modules.model.infrastructure.persistence.repository;

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

/**
 * 模型模块领域仓储适配器（infrastructure 层）。
 *
 * <p>实现 domain 层 {@link ModelRepository} 端口，聚合三个单表 Repository：
 * 读取时把实体转换为领域对象，写入时反向转换。厂商密钥在这一层
 * 通过 {@link SecretCipher} 完成加密/解密，保证密文只出现在数据库中。
 * 标记 {@code @Primary} 使其成为运行时唯一注入的仓储实现。</p>
 */
@Primary
@Repository
public class MybatisModelRepository implements ModelRepository {
    private final ProviderRepository providers;
    private final com.agentplatform.modules.model.infrastructure.persistence.repository.ModelRepository models;
    private final RoutingPolicyRepository routing;
    private final SecretCipher cipher;

    /**
     * 构造适配器。
     *
     * @param providers 厂商单表仓储
     * @param models    模型单表仓储
     * @param routing   路由策略单表仓储
     * @param cipher    密钥加解密组件
     */
    public MybatisModelRepository(ProviderRepository providers,
                                  com.agentplatform.modules.model.infrastructure.persistence.repository.ModelRepository models,
                                  RoutingPolicyRepository routing, SecretCipher cipher) {
        this.providers = providers; this.models = models; this.routing = routing; this.cipher = cipher;
    }

    /**
     * 查询全部厂商，并从密文解密出真实密钥。
     */
    @Override public List<Provider> findProviders() { return providers.findAll().stream().map(p -> p.toDomain(cipher)).toList(); }

    /**
     * 按 ID 查询厂商，并从密文解密出真实密钥。
     */
    @Override public Optional<Provider> findProvider(String id) { return providers.findById(id).map(p -> p.toDomain(cipher)); }

    /**
     * 查询全部模型。
     */
    @Override public List<ModelInfo> findModels() { return models.findAll().stream().map(ModelEntity::toDomain).toList(); }

    /**
     * 按 ID 查询模型。
     */
    @Override public Optional<ModelInfo> findModel(String id) { return models.findById(id).map(ModelEntity::toDomain); }

    /**
     * 保存厂商，写入前对真实密钥加密。
     */
    @Override public void saveProvider(Provider provider) { providers.save(ProviderEntity.from(provider, cipher)); }

    /**
     * 保存模型。
     */
    @Override public void saveModel(ModelInfo model) { models.save(ModelEntity.from(model)); }

    /**
     * 按 ID 删除模型。
     */
    @Override public void deleteModel(String id) { models.deleteById(id); }

    /**
     * 读取全局路由策略；数据库中无记录时返回内置默认策略。
     */
    @Override public RoutingPolicy routing() {
        return routing.findById("default").map(RoutingPolicyEntity::toDomain)
                .orElse(new RoutingPolicy("质量优先", "claude-sonnet-5-5", List.of(), "华东 1"));
    }

    /**
     * 保存全局路由策略（主键固定 default）。
     */
    @Override public void saveRouting(RoutingPolicy policy) { routing.save(RoutingPolicyEntity.from(policy)); }

    /**
     * 按 ID 删除厂商。
     */
    @Override public void deleteProvider(String id) { providers.deleteById(id); }

    /**
     * 删除指定厂商下的全部模型。
     */
    @Override public void deleteModelsByProvider(String providerId) { models.deleteByProviderId(providerId); }
}
