package com.agentplatform.modules.knowledge.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import com.agentplatform.modules.knowledge.domain.model.Document;

/**
 * 文档持久化实体（对应表 {@code documents}）。
 *
 * <p>{@code sizeLabel} 映射到列 {@code size_label}（避免 SQL 关键字冲突）；
 * {@code sourceType} 为 null 时按 FILE 处理，兼容旧数据。</p>
 */
@TableName(value = "documents", autoResultMap = true)
public class DocumentEntity {
    /** 主键，由应用层生成。 */
    @TableId(type = IdType.INPUT) private String id;
    /** 所属数据集 ID。 */
    @TableField(value = "dataset_id") private String datasetId;
    /** 文档名称。 */
    private String name;
    /** 版本号。 */
    private int version;
    /** 分块数量。 */
    private long chunks;
    /** 展示用文件大小文本，映射到 {@code size_label} 列。 */
    @TableField(value = "size_label") private String sizeLabel;
    /** 处理状态。 */
    private String status;
    /** 更新时间展示文本。 */
    private String updated;
    /** 文件存储键。 */
    @TableField(value = "storage_key") private String storageKey;
    /** 文件 MIME 类型。 */
    @TableField(value = "content_type") private String contentType;
    /** 内容 SHA-256 摘要。 */
    private String checksum;
    /** 来源类型：FILE 或 WEB。 */
    @TableField(value = "source_type") private String sourceType;
    /** 网页来源地址。 */
    @TableField(value = "source_url") private String sourceUrl;

    /**
     * 由领域模型转换为持久化实体。
     *
     * @param d 文档领域模型
     * @return 持久化实体
     */
    public static DocumentEntity from(Document d) {
        DocumentEntity e = new DocumentEntity();
        e.id = d.id(); e.datasetId = d.datasetId(); e.name = d.name(); e.version = d.version();
        e.chunks = d.chunks(); e.sizeLabel = d.size(); e.status = d.status(); e.updated = d.updated();
        e.storageKey = d.storageKey(); e.contentType = d.contentType(); e.checksum = d.checksum();
        e.sourceType = d.sourceType(); e.sourceUrl = d.sourceUrl();
        return e;
    }

    /**
     * 由持久化实体还原领域模型；{@code sourceType} 为 null 时默认为 FILE。
     *
     * @return 文档领域模型
     */
    public Document toDomain() {
        return new Document(id, datasetId, name, version, chunks, sizeLabel, status, updated,
                storageKey, contentType, checksum,
                sourceType == null ? "FILE" : sourceType, sourceUrl);
    }
}
