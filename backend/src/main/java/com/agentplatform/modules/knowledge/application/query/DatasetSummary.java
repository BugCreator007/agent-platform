package com.agentplatform.modules.knowledge.application.query;

import com.agentplatform.modules.knowledge.domain.model.Dataset;

import java.util.List;

public record DatasetSummary(
        String id, String name, String owner, String description,
        String embeddingModel, String indexType, String metric, String parser,
        String chunkStrategy, int chunkSize, int overlap, String retrievalMode,
        int topK, double threshold, boolean rerank, boolean needsReindex,
        String status, long docs, long chunks, String updated, List<String> authAgents
) {
    public static DatasetSummary from(Dataset d) {
        return new DatasetSummary(d.id(), d.name(), d.owner(), d.description(), d.embeddingModel(),
                d.indexType(), d.metric(), d.parser(), d.chunkStrategy(), d.chunkSize(), d.overlap(),
                d.retrievalMode(), d.topK(), d.threshold(), d.rerank(), d.needsReindex(), d.status(),
                d.docs(), d.chunks(), d.updated(), d.authAgents());
    }
}
