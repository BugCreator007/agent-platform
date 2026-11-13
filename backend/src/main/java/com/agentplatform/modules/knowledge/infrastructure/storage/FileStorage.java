package com.agentplatform.modules.knowledge.infrastructure.storage;

public interface FileStorage {
    String store(String datasetId, String filename, byte[] content, String contentType);
    byte[] read(String storageKey);
}
