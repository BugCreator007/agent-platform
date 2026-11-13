package com.agentplatform.modules.knowledge.domain.repository;

import com.agentplatform.modules.knowledge.domain.model.DocumentChunk;

import java.util.List;

public interface ChunkRepository {
    void saveAll(List<DocumentChunk> chunks);
    List<DocumentChunk> findByDataset(String datasetId);
    List<DocumentChunk> findByDocument(String documentId);
    void deleteByDocument(String documentId);
}
