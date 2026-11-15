package com.agentplatform.modules.model.infrastructure.persistence.repository;

import com.agentplatform.modules.model.infrastructure.persistence.mapper.ModelMapper;

import com.agentplatform.modules.model.infrastructure.persistence.entity.ModelEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ModelRepository {
    private final ModelMapper mapper;

    public ModelRepository(ModelMapper mapper) { this.mapper = mapper; }

    public List<ModelEntity> findAll() { return mapper.selectList(null); }
    public Optional<ModelEntity> findById(String id) { return Optional.ofNullable(mapper.selectById(id)); }
    public void save(ModelEntity model) { mapper.insertOrUpdate(model); }
    public void deleteById(String id) { mapper.deleteById(id); }
    public void deleteByProviderId(String providerId) { mapper.deleteByProviderId(providerId); }
}
