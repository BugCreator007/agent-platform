package com.agentplatform.modules.knowledge.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import com.agentplatform.modules.knowledge.domain.model.Document;

@TableName(value = "documents", autoResultMap = true)
public class DocumentEntity {
    @TableId(type = IdType.INPUT) private String id;
    @TableField(value = "dataset_id") private String datasetId;
    private String name;
    private int version;
    private long chunks;
    @TableField(value = "size_label") private String sizeLabel;
    private String status;
    private String updated;
    @TableField(value = "storage_key") private String storageKey;
    @TableField(value = "content_type") private String contentType;
    private String checksum;
    @TableField(value = "source_type") private String sourceType;
    @TableField(value = "source_url") private String sourceUrl;

    public static DocumentEntity from(Document d) {
        DocumentEntity e = new DocumentEntity();
        e.id = d.id(); e.datasetId = d.datasetId(); e.name = d.name(); e.version = d.version();
        e.chunks = d.chunks(); e.sizeLabel = d.size(); e.status = d.status(); e.updated = d.updated();
        e.storageKey = d.storageKey(); e.contentType = d.contentType(); e.checksum = d.checksum();
        e.sourceType = d.sourceType(); e.sourceUrl = d.sourceUrl();
        return e;
    }

    public Document toDomain() {
        return new Document(id, datasetId, name, version, chunks, sizeLabel, status, updated,
                storageKey, contentType, checksum,
                sourceType == null ? "FILE" : sourceType, sourceUrl);
    }
}
