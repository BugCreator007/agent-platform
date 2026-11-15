package com.agentplatform.modules.model.infrastructure.persistence.repository;

import com.agentplatform.modules.model.infrastructure.persistence.mapper.ProviderMapper;

import com.agentplatform.modules.model.infrastructure.persistence.entity.ProviderEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ProviderRepository {
    private final ProviderMapper mapper;

    public ProviderRepository(ProviderMapper mapper) { this.mapper = mapper; }

    public List<ProviderEntity> findAll() { return mapper.selectList(null); }
    public Optional<ProviderEntity> findById(String id) { return Optional.ofNullable(mapper.selectById(id)); }
    public void save(ProviderEntity provider) { mapper.insertOrUpdate(provider); }
    public void deleteById(String id) { mapper.deleteById(id); }
}
