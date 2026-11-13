package com.agentplatform.modules.knowledge.application.query;

public record RetrievalHit(String source, int chunk, double score, String text) {
}
