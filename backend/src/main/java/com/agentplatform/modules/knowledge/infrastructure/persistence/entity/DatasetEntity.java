package com.agentplatform.modules.knowledge.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.agentplatform.common.persistence.StringListTypeHandler;

import com.agentplatform.modules.knowledge.domain.model.Dataset;

import java.util.List;

/**
 * 数据集持久化实体（对应表 {@code datasets}）。
 *
 * <p>通过 MyBatis-Plus 注解完成表名与列名映射；{@code authAgents} 以 JSON 字符串存储，
 * 由 {@link StringListTypeHandler} 负责转换。仅用于基础设施层，不直接暴露给应用层。</p>
 */
@TableName(value = "datasets", autoResultMap = true)
public class DatasetEntity {
    /** 主键，由应用层生成（非自增）。 */
    @TableId(type = IdType.INPUT) private String id;
    /** 数据集名称。 */
    private String name;
    /** 归属方/负责人。 */
    private String owner;
    /** 描述。 */
    private String description;
    /** Embedding 模型标识。 */
    @TableField(value = "embedding_model") private String embeddingModel;
    /** 向量索引类型。 */
    @TableField(value = "index_type") private String indexType;
    /** 相似度度量方式。 */
    private String metric;
    /** 文档解析器。 */
    private String parser;
    /** 分块策略。 */
    @TableField(value = "chunk_strategy") private String chunkStrategy;
    /** 分块大小。 */
    @TableField(value = "chunk_size") private int chunkSize;
    /** 分块重叠大小。 */
    private int overlap;
    /** 检索模式。 */
    @TableField(value = "retrieval_mode") private String retrievalMode;
    /** 检索返回数量。 */
    @TableField(value = "top_k") private int topK;
    /** 向量相似度阈值。 */
    private double threshold;
    /** 是否启用重排。 */
    private boolean rerank;
    /** 是否需要重建索引。 */
    @TableField(value = "needs_reindex") private boolean needsReindex;
    /** 数据集状态。 */
    private String status;
    /** 文档数量。 */
    private long docs;
    /** 分块总数。 */
    private long chunks;
    /** 更新时间展示文本。 */
    private String updated;
    /** 授权智能体 ID 列表，以 JSON 文本存储。 */
    @TableField(value = "auth_agents", typeHandler = StringListTypeHandler.class) private List<String> authAgents;

    /**
     * 由领域聚合转换为持久化实体。
     *
     * @param d 数据集聚合
     * @return 持久化实体
     */
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

    /**
     * 由持久化实体还原领域聚合；{@code authAgents} 为 null 时按空列表处理。
     *
     * @return 数据集聚合
     */
    public Dataset toDomain() {
        return Dataset.restore(id, name, owner, description, embeddingModel, indexType, metric, parser,
                chunkStrategy, chunkSize, overlap, retrievalMode, topK, threshold, rerank,
                needsReindex, status, docs, chunks, updated, authAgents == null ? List.of() : authAgents);
    }
}
