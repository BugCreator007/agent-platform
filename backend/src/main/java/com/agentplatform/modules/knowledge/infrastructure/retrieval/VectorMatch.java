package com.agentplatform.modules.knowledge.infrastructure.retrieval;

public record VectorMatch(String id, String documentId, int chunkIndex, String content, double score) {
}
