package com.agentplatform.modules.knowledge.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName(value = "document_chunks", autoResultMap = true)
public class DocumentChunkEntity {
    @TableId(type = IdType.INPUT) private String id;
    @TableField(value = "document_id") private String documentId;
    @TableField(value = "dataset_id") private String datasetId;
    @TableField(value = "chunk_index") private int chunkIndex;
    private String content;
    @TableField(value = "token_count") private int tokenCount;
    @TableField(value = "embedding_json") private String embeddingJson;
    @TableField(value = "metadata_json") private String metadataJson;

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
