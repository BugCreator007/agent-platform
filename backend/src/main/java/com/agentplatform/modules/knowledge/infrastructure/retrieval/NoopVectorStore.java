package com.agentplatform.modules.knowledge.infrastructure.retrieval;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "app.vector.milvus.enabled", havingValue = "false", matchIfMissing = true)
public class NoopVectorStore implements VectorStore {
    public NoopVectorStore() { org.slf4j.LoggerFactory.getLogger(NoopVectorStore.class).info("NoopVectorStore active"); }
    @Override public void upsert(List<VectorRecord> records) { }
    @Override public List<VectorMatch> search(String datasetId, List<Double> vector, int topK) { return List.of(); }
    @Override public void deleteByDocument(String documentId) { }
}
