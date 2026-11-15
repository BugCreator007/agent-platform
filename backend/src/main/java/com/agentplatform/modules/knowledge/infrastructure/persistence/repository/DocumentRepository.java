package com.agentplatform.modules.knowledge.infrastructure.persistence.repository;

import com.agentplatform.modules.knowledge.infrastructure.persistence.mapper.DocumentMapper;

import com.agentplatform.modules.knowledge.infrastructure.persistence.entity.DocumentEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class DocumentRepository {
    private final DocumentMapper mapper;

    public DocumentRepository(DocumentMapper mapper) { this.mapper = mapper; }

    public List<DocumentEntity> findByDataset(String datasetId) { return mapper.findByDatasetId(datasetId); }
    public Optional<DocumentEntity> findById(String id) { return Optional.ofNullable(mapper.selectById(id)); }
    public void save(DocumentEntity document) { mapper.insertOrUpdate(document); }
    public void deleteById(String id) { mapper.deleteById(id); }
}
