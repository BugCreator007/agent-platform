package com.agentplatform.modules.knowledge.interfaces.dto;

import java.util.List;

public record UpdateDatasetRequest(
        String name, String owner, String description,
        String embeddingModel, String indexType, String metric, String parser,
        String chunkStrategy, Integer chunkSize, Integer overlap,
        String retrievalMode, Integer topK, Double threshold, Boolean rerank,
        List<String> authAgents, Boolean needsReindex
) {
}
