package com.agentplatform.modules.knowledge.infrastructure.retrieval;

import java.util.List;

public interface KeywordIndex {
    void index(List<VectorRecord> records);
    List<VectorMatch> search(String datasetId, String query, int topK);
    void deleteByDocument(String documentId);
}
