package com.agentplatform.modules.knowledge.infrastructure.persistence.entity;

import com.agentplatform.common.persistence.StringListConverter;
import com.agentplatform.modules.knowledge.domain.model.Dataset;
import jakarta.persistence.*;

import java.util.List;

@Entity
@Table(name = "datasets")
public class DatasetEntity {
    @Id private String id;
    private String name;
    private String owner;
    @Column(columnDefinition = "TEXT") private String description;
    @Column(name = "embedding_model") private String embeddingModel;
    @Column(name = "index_type") private String indexType;
    private String metric;
    private String parser;
    @Column(name = "chunk_strategy") private String chunkStrategy;
    @Column(name = "chunk_size") private int chunkSize;
    private int overlap;
    @Column(name = "retrieval_mode") private String retrievalMode;
    @Column(name = "top_k") private int topK;
    private double threshold;
    private boolean rerank;
    @Column(name = "needs_reindex") private boolean needsReindex;
    private String status;
    private long docs;
    private long chunks;
    private String updated;
    @Convert(converter = StringListConverter.class)
    @Column(name = "auth_agents", columnDefinition = "TEXT") private List<String> authAgents;

    public static DatasetEntity from(Dataset d) {
        DatasetEntity e = new DatasetEntity();
        e.id = d.id(); e.name = d.name(); e.owner = d.owner(); e.description = d.description();
        e.embeddingModel = d.embeddingModel(); e.indexType = d.indexType(); e.metric = d.metric();
        e.parser = d.parser(); e.chunkStrategy = d.chunkStrategy(); e.chunkSize = d.chunkSize();
        e.overlap = d.overlap(); e.retrievalMode = d.retrievalMode(); e.topK = d.topK();
        e.threshold = d.threshold(); e.rerank = d.rerank(); e.needsReindex = d.needsReindex();
        e.status = d.status(); e.docs = d.docs(); e.chunks = d.chunks(); e.updated = d.updated();
        e.authAgents = d.authAgents();
        return e;
    }

    public Dataset toDomain() {
        return Dataset.restore(id, name, owner, description, embeddingModel, indexType, metric, parser,
                chunkStrategy, chunkSize, overlap, retrievalMode, topK, threshold, rerank,
                needsReindex, status, docs, chunks, updated, authAgents == null ? List.of() : authAgents);
    }
}
