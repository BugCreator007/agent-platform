package com.agentplatform.modules.knowledge.infrastructure.persistence.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "document_chunks")
public class DocumentChunkEntity {
    @Id private String id;
    @Column(name = "document_id") private String documentId;
    @Column(name = "dataset_id") private String datasetId;
    @Column(name = "chunk_index") private int chunkIndex;
    @Column(columnDefinition = "TEXT") private String content;
    @Column(name = "token_count") private int tokenCount;
    @Column(name = "embedding_json", columnDefinition = "LONGTEXT") private String embeddingJson;
    @Column(name = "metadata_json", columnDefinition = "TEXT") private String metadataJson;

    public DocumentChunkEntity() {}
    public DocumentChunkEntity(String id, String documentId, String datasetId, int chunkIndex,
                               String content, int tokenCount, String embeddingJson, String metadataJson) {
        this.id = id; this.documentId = documentId; this.datasetId = datasetId; this.chunkIndex = chunkIndex;
        this.content = content; this.tokenCount = tokenCount; this.embeddingJson = embeddingJson; this.metadataJson = metadataJson;
    }
    public String getId() { return id; }
    public String getDocumentId() { return documentId; }
    public String getDatasetId() { return datasetId; }
    public int getChunkIndex() { return chunkIndex; }
    public String getContent() { return content; }
    public int getTokenCount() { return tokenCount; }
    public String getEmbeddingJson() { return embeddingJson; }
    public String getMetadataJson() { return metadataJson; }
}
