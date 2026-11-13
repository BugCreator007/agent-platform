package com.agentplatform.modules.knowledge.infrastructure.persistence;

import com.agentplatform.modules.knowledge.domain.model.DocumentChunk;
import com.agentplatform.modules.knowledge.domain.repository.ChunkRepository;
import com.agentplatform.modules.knowledge.infrastructure.persistence.entity.DocumentChunkEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public class JpaChunkRepository implements ChunkRepository {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final DocumentChunkJpaRepository jpa;
    public JpaChunkRepository(DocumentChunkJpaRepository jpa) { this.jpa = jpa; }

    @Override
    public void saveAll(List<DocumentChunk> chunks) {
        jpa.saveAll(chunks.stream().map(c -> {
            try {
                return new DocumentChunkEntity(c.id(), c.documentId(), c.datasetId(), c.chunkIndex(),
                        c.content(), c.tokenCount(), MAPPER.writeValueAsString(c.embedding()), null);
            } catch (Exception e) { throw new IllegalArgumentException("embedding serialize failed", e); }
        }).toList());
    }

    @Override public List<DocumentChunk> findByDataset(String datasetId) {
        return jpa.findByDatasetId(datasetId).stream().map(this::toDomain).toList();
    }
    @Override public List<DocumentChunk> findByDocument(String documentId) {
        return jpa.findByDocumentId(documentId).stream().map(this::toDomain).toList();
    }
    @Override @Transactional public void deleteByDocument(String documentId) { jpa.deleteByDocumentId(documentId); }

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
