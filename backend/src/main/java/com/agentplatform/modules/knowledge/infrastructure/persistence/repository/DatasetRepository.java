package com.agentplatform.modules.knowledge.infrastructure.persistence.repository;

import com.agentplatform.modules.knowledge.infrastructure.persistence.mapper.DatasetMapper;

import com.agentplatform.modules.knowledge.infrastructure.persistence.entity.DatasetEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class DatasetRepository {
    private final DatasetMapper mapper;

    public DatasetRepository(DatasetMapper mapper) { this.mapper = mapper; }

    public List<DatasetEntity> findAll() { return mapper.selectList(null); }
    public Optional<DatasetEntity> findById(String id) { return Optional.ofNullable(mapper.selectById(id)); }
    public void save(DatasetEntity dataset) { mapper.insertOrUpdate(dataset); }
    public void deleteById(String id) { mapper.deleteById(id); }
}
