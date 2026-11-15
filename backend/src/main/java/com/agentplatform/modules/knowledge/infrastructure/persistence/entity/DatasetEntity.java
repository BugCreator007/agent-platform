package com.agentplatform.modules.knowledge.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.agentplatform.common.persistence.StringListTypeHandler;

import com.agentplatform.modules.knowledge.domain.model.Dataset;

import java.util.List;

@TableName(value = "datasets", autoResultMap = true)
public class DatasetEntity {
    @TableId(type = IdType.INPUT) private String id;
    private String name;
    private String owner;
    private String description;
    @TableField(value = "embedding_model") private String embeddingModel;
    @TableField(value = "index_type") private String indexType;
    private String metric;
    private String parser;
    @TableField(value = "chunk_strategy") private String chunkStrategy;
    @TableField(value = "chunk_size") private int chunkSize;
    private int overlap;
    @TableField(value = "retrieval_mode") private String retrievalMode;
    @TableField(value = "top_k") private int topK;
    private double threshold;
    private boolean rerank;
    @TableField(value = "needs_reindex") private boolean needsReindex;
    private String status;
    private long docs;
    private long chunks;
    private String updated;
    @TableField(value = "auth_agents", typeHandler = StringListTypeHandler.class) private List<String> authAgents;

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
