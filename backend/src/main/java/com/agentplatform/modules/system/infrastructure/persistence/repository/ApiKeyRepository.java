package com.agentplatform.modules.system.infrastructure.persistence.repository;

import com.agentplatform.modules.system.infrastructure.persistence.mapper.ApiKeyMapper;

import com.agentplatform.modules.system.infrastructure.persistence.entity.ApiKeyEntity;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ApiKeyRepository {
    private final ApiKeyMapper mapper;

    public ApiKeyRepository(ApiKeyMapper mapper) { this.mapper = mapper; }

    public List<ApiKeyEntity> findAll() { return mapper.selectList(null); }
    public void save(ApiKeyEntity key) { mapper.insertOrUpdate(key); }
}
