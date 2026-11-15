package com.agentplatform.modules.model.infrastructure.persistence.repository;

import com.agentplatform.modules.model.infrastructure.persistence.mapper.ProviderMapper;

import com.agentplatform.modules.model.infrastructure.persistence.entity.ProviderEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 厂商单表仓储（infrastructure 层），包装 {@link ProviderMapper} 的实体级读写。
 */
@Repository
public class ProviderRepository {
    private final ProviderMapper mapper;

    /**
     * 构造仓储。
     *
     * @param mapper 厂商表 Mapper
     */
    public ProviderRepository(ProviderMapper mapper) { this.mapper = mapper; }

    /**
     * 查询全部厂商实体。
     *
     * @return 实体列表
     */
    public List<ProviderEntity> findAll() { return mapper.selectList(null); }

    /**
     * 按 ID 查询厂商实体。
     *
     * @param id 厂商 ID
     * @return 实体（可能为空）
     */
    public Optional<ProviderEntity> findById(String id) { return Optional.ofNullable(mapper.selectById(id)); }

    /**
     * 新增或更新厂商实体。
     *
     * @param provider 实体
     */
    public void save(ProviderEntity provider) { mapper.insertOrUpdate(provider); }

    /**
     * 按 ID 删除厂商实体。
     *
     * @param id 厂商 ID
     */
    public void deleteById(String id) { mapper.deleteById(id); }
}
