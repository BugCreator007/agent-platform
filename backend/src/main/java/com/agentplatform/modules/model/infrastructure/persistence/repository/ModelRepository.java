package com.agentplatform.modules.model.infrastructure.persistence.repository;

import com.agentplatform.modules.model.infrastructure.persistence.mapper.ModelMapper;

import com.agentplatform.modules.model.infrastructure.persistence.entity.ModelEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 模型单表仓储（infrastructure 层）。
 *
 * <p>包装 {@link ModelMapper}，只做实体层读写；领域端口适配器
 * {@code MybatisModelRepository} 会在此之上完成实体与领域对象的转换。</p>
 */
@Repository
public class ModelRepository {
    private final ModelMapper mapper;

    /**
     * 构造仓储。
     *
     * @param mapper 模型表 Mapper
     */
    public ModelRepository(ModelMapper mapper) { this.mapper = mapper; }

    /**
     * 查询全部模型实体。
     *
     * @return 实体列表
     */
    public List<ModelEntity> findAll() { return mapper.selectList(null); }

    /**
     * 按 ID 查询模型实体。
     *
     * @param id 模型 ID
     * @return 实体（可能为空）
     */
    public Optional<ModelEntity> findById(String id) { return Optional.ofNullable(mapper.selectById(id)); }

    /**
     * 新增或更新模型实体。
     *
     * @param model 实体
     */
    public void save(ModelEntity model) { mapper.insertOrUpdate(model); }

    /**
     * 按 ID 删除模型实体。
     *
     * @param id 模型 ID
     */
    public void deleteById(String id) { mapper.deleteById(id); }

    /**
     * 删除指定厂商下的全部模型实体。
     *
     * @param providerId 厂商 ID
     */
    public void deleteByProviderId(String providerId) { mapper.deleteByProviderId(providerId); }
}
