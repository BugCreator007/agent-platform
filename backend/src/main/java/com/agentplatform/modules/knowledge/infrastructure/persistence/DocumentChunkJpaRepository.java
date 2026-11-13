package com.agentplatform.modules.knowledge.infrastructure.persistence;

import com.agentplatform.modules.knowledge.infrastructure.persistence.entity.DocumentChunkEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentChunkJpaRepository extends JpaRepository<DocumentChunkEntity, String> {
    List<DocumentChunkEntity> findByDatasetId(String datasetId);
    List<DocumentChunkEntity> findByDocumentId(String documentId);
    void deleteByDocumentId(String documentId);
}
