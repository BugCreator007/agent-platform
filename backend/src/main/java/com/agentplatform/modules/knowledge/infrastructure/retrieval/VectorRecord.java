package com.agentplatform.modules.knowledge.infrastructure.retrieval;

import java.util.List;

public record VectorRecord(String id, String documentId, String datasetId, int chunkIndex,
                           String content, List<Double> embedding) {
}
