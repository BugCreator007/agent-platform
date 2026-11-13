package com.agentplatform.modules.knowledge.infrastructure.retrieval;

import java.util.List;

public interface VectorStore {
    void upsert(List<VectorRecord> records);
    List<VectorMatch> search(String datasetId, List<Double> vector, int topK);
    void deleteByDocument(String documentId);
}
