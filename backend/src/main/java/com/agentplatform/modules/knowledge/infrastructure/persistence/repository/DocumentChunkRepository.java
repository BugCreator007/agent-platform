package com.agentplatform.modules.knowledge.infrastructure.persistence.repository;

import com.agentplatform.modules.knowledge.infrastructure.persistence.mapper.DocumentChunkMapper;

import com.agentplatform.modules.knowledge.domain.model.DocumentChunk;
import com.agentplatform.modules.knowledge.domain.repository.ChunkRepository;
import com.agentplatform.modules.knowledge.infrastructure.persistence.entity.DocumentChunkEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public class DocumentChunkRepository implements ChunkRepository {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final DocumentChunkMapper mapper;

    public DocumentChunkRepository(DocumentChunkMapper mapper) { this.mapper = mapper; }

    @Override
    public void saveAll(List<DocumentChunk> chunks) {
        chunks.forEach(c -> {
            try {
                mapper.insertOrUpdate(new DocumentChunkEntity(c.id(), c.documentId(), c.datasetId(), c.chunkIndex(),
                        c.content(), c.tokenCount(), MAPPER.writeValueAsString(c.embedding()), null));
            } catch (Exception e) { throw new IllegalArgumentException("embedding serialize failed", e); }
        });
    }

    @Override public List<DocumentChunk> findByDataset(String datasetId) {
        return mapper.findByDatasetId(datasetId).stream().map(this::toDomain).toList();
    }
    @Override public List<DocumentChunk> findByDocument(String documentId) {
        return mapper.findByDocumentId(documentId).stream().map(this::toDomain).toList();
    }
    @Override @Transactional public void deleteByDocument(String documentId) { mapper.deleteByDocumentId(documentId); }

    private DocumentChunk toDomain(DocumentChunkEntity e) {
        try {
            List<Double> embedding = e.getEmbeddingJson() == null || e.getEmbeddingJson().isBlank()
                    ? List.of() : MAPPER.readValue(e.getEmbeddingJson(), new TypeReference<>() {});
            return new DocumentChunk(e.getId(), e.getDocumentId(), e.getDatasetId(), e.getChunkIndex(),
                    e.getContent(), e.getTokenCount(), embedding);
        } catch (Exception ex) { return new DocumentChunk(e.getId(), e.getDocumentId(), e.getDatasetId(),
                e.getChunkIndex(), e.getContent(), e.getTokenCount(), List.of()); }
    }
}
