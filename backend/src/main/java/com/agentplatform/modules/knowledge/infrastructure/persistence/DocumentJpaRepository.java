package com.agentplatform.modules.knowledge.infrastructure.persistence;

import com.agentplatform.modules.knowledge.infrastructure.persistence.entity.DocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentJpaRepository extends JpaRepository<DocumentEntity, String> {
    List<DocumentEntity> findByDatasetId(String datasetId);
    void deleteByDatasetIdAndId(String datasetId, String id);
}
