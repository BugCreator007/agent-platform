package com.agentplatform.modules.knowledge.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 文档分块持久化实体（对应表 {@code document_chunks}）。
 *
 * <p>向量以 JSON 数组文本存储在 {@code embedding_json} 列，避免依赖数据库原生向量类型；
 * 该列同时作为未启用 Milvus 时的数据库内向量检索回退数据源。</p>
 */
@TableName(value = "document_chunks", autoResultMap = true)
public class DocumentChunkEntity {
    /** 主键，由应用层生成。 */
    @TableId(type = IdType.INPUT) private String id;
    /** 所属文档 ID。 */
    @TableField(value = "document_id") private String documentId;
    /** 所属数据集 ID，用于按数据集批量检索。 */
    @TableField(value = "dataset_id") private String datasetId;
    /** 分块在文档内的顺序下标。 */
    @TableField(value = "chunk_index") private int chunkIndex;
    /** 分块文本。 */
    private String content;
    /** 近似 token 数。 */
    @TableField(value = "token_count") private int tokenCount;
    /** 向量 JSON 文本，如 {@code [0.1,0.2,...]}。 */
    @TableField(value = "embedding_json") private String embeddingJson;
    /** 预留的元数据 JSON 列。 */
    @TableField(value = "metadata_json") private String metadataJson;

    /** MyBatis 反射实例化所需的无参构造器。 */
    public DocumentChunkEntity() {}
    /**
     * 全字段构造器。
     *
     * @param id            主键
     * @param documentId    所属文档 ID
     * @param datasetId     所属数据集 ID
     * @param chunkIndex    分块下标
     * @param content       分块文本
     * @param tokenCount    近似 token 数
     * @param embeddingJson 向量 JSON 文本
     * @param metadataJson  元数据 JSON 文本
     */
    public DocumentChunkEntity(String id, String documentId, String datasetId, int chunkIndex,
                               String content, int tokenCount, String embeddingJson, String metadataJson) {
        this.id = id; this.documentId = documentId; this.datasetId = datasetId; this.chunkIndex = chunkIndex;
        this.content = content; this.tokenCount = tokenCount; this.embeddingJson = embeddingJson; this.metadataJson = metadataJson;
    }
    /**
     * @return 分块主键
     */
    public String getId() { return id; }
    /**
     * @return 所属文档 ID
     */
    public String getDocumentId() { return documentId; }
    /**
     * @return 所属数据集 ID
     */
    public String getDatasetId() { return datasetId; }
    /**
     * @return 分块下标
     */
    public int getChunkIndex() { return chunkIndex; }
    /**
     * @return 分块文本
     */
    public String getContent() { return content; }
    /**
     * @return 近似 token 数
     */
    public int getTokenCount() { return tokenCount; }
    /**
     * @return 向量 JSON 文本
     */
    public String getEmbeddingJson() { return embeddingJson; }
    /**
     * @return 元数据 JSON 文本
     */
    public String getMetadataJson() { return metadataJson; }
}
