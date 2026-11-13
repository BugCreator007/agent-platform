package com.agentplatform.modules.knowledge.infrastructure.persistence.entity;

import com.agentplatform.modules.knowledge.domain.model.Document;
import jakarta.persistence.*;

@Entity
@Table(name = "documents")
public class DocumentEntity {
    @Id private String id;
    @Column(name = "dataset_id") private String datasetId;
    private String name;
    private int version;
    private long chunks;
    @Column(name = "size_label") private String sizeLabel;
    private String status;
    private String updated;
    @Column(name = "storage_key") private String storageKey;
    @Column(name = "content_type") private String contentType;
    private String checksum;
    @Column(name = "source_type") private String sourceType;
    @Column(name = "source_url") private String sourceUrl;

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
