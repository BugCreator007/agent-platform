package com.agentplatform.modules.knowledge.domain.model;

public record Document(
        String id, String datasetId, String name, int version, long chunks,
        String size, String status, String updated,
        String storageKey, String contentType, String checksum,
        String sourceType, String sourceUrl
) {
    public Document(String id, String datasetId, String name, int version, long chunks,
                    String size, String status, String updated) {
        this(id, datasetId, name, version, chunks, size, status, updated, null, null, null,
                "FILE", null);
    }

    public Document(String id, String datasetId, String name, int version, long chunks,
                    String size, String status, String updated,
                    String storageKey, String contentType, String checksum) {
        this(id, datasetId, name, version, chunks, size, status, updated, storageKey, contentType,
                checksum, "FILE", null);
    }
}
