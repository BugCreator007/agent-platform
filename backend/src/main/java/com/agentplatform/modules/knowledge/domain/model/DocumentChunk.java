package com.agentplatform.modules.knowledge.domain.model;

import java.util.List;

public record DocumentChunk(String id, String documentId, String datasetId, int chunkIndex,
                            String content, int tokenCount, List<Double> embedding) {
}
