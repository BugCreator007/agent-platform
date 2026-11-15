package com.agentplatform.modules.model.domain.repository;

import com.agentplatform.modules.model.domain.model.ModelInfo;
import com.agentplatform.modules.model.domain.model.Provider;
import com.agentplatform.modules.model.domain.model.RoutingPolicy;

import java.util.List;
import java.util.Optional;

/**
 * 模型模块领域仓储端口（domain 层）。
 *
 * <p>定义厂商、模型与路由策略的读取与持久化能力，由基础设施层的
 * {@code MybatisModelRepository} 实现。应用层只依赖本接口，不感知数据库细节；
 * 密钥加解密由适配器负责，领域对象始终持有明文（使用时）。</p>
 */
public interface ModelRepository {
    /**
     * 查询全部厂商。
     *
     * @return 厂商列表
     */
    List<Provider> findProviders();

    /**
     * 按 ID 查询厂商。
     *
     * @param id 厂商 ID
     * @return 厂商（可能为空）
     */
    Optional<Provider> findProvider(String id);

    /**
     * 查询全部模型。
     *
     * @return 模型列表
     */
    List<ModelInfo> findModels();

    /**
     * 按 ID 查询模型。
     *
     * @param id 模型 ID
     * @return 模型（可能为空）
     */
    Optional<ModelInfo> findModel(String id);

    /**
     * 保存厂商（新增或更新）。
     *
     * @param provider 厂商
     */
    void saveProvider(Provider provider);

    /**
     * 保存模型（新增或更新）。
     *
     * @param model 模型
     */
    void saveModel(ModelInfo model);

    /**
     * 按 ID 删除模型。
     *
     * @param id 模型 ID
     */
    void deleteModel(String id);

    /**
     * 读取全局路由策略；未配置时返回默认策略。
     *
     * @return 路由策略
     */
    RoutingPolicy routing();

    /**
     * 保存全局路由策略。
     *
     * @param routing 路由策略
     */
    void saveRouting(RoutingPolicy routing);

    /**
     * 按 ID 删除厂商。
     *
     * @param id 厂商 ID
     */
    void deleteProvider(String id);

    /**
     * 删除指定厂商下的全部模型。
     *
     * @param providerId 厂商 ID
     */
    void deleteModelsByProvider(String providerId);
}
