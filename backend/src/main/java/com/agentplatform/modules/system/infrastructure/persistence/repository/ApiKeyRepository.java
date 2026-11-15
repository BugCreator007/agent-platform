package com.agentplatform.modules.system.infrastructure.persistence.repository;

import com.agentplatform.modules.system.infrastructure.persistence.mapper.ApiKeyMapper;

import com.agentplatform.modules.system.infrastructure.persistence.entity.ApiKeyEntity;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * API Key 数据访问仓储（Mapper 包装）。
 *
 * <p>位于基础设施持久化层，封装 {@link ApiKeyMapper}，只操作实体；
 * 领域模型转换与端口适配由 {@code MybatisSystemRepository} 负责。</p>
 */
@Repository
public class ApiKeyRepository {
    private final ApiKeyMapper mapper;

    /**
     * @param mapper API Key Mapper
     */
    public ApiKeyRepository(ApiKeyMapper mapper) { this.mapper = mapper; }

    /**
     * 查询全部 API Key。
     *
     * @return 实体列表
     */
    public List<ApiKeyEntity> findAll() { return mapper.selectList(null); }

    /**
     * 新增或更新 API Key（存在则更新，不存在则插入）。
     *
     * @param key 密钥实体
     */
    public void save(ApiKeyEntity key) { mapper.insertOrUpdate(key); }
}
