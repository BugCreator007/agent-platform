package com.agentplatform.modules.knowledge.infrastructure.retrieval;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "app.search.elasticsearch.enabled", havingValue = "false", matchIfMissing = true)
public class NoopKeywordIndex implements KeywordIndex {
    @Override public void index(List<VectorRecord> records) { }
    @Override public List<VectorMatch> search(String datasetId, String query, int topK) { return List.of(); }
    @Override public void deleteByDocument(String documentId) { }
}
