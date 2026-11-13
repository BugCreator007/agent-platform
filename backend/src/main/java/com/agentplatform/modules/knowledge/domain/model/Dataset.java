package com.agentplatform.modules.knowledge.domain.model;

import java.util.ArrayList;
import java.util.List;

public class Dataset {
    private final String id;
    private String name;
    private String owner;
    private String description;
    private String embeddingModel;
    private String indexType;
    private String metric;
    private String parser;
    private String chunkStrategy;
    private int chunkSize;
    private int overlap;
    private String retrievalMode;
    private int topK;
    private double threshold;
    private boolean rerank;
    private boolean needsReindex;
    private String status;
    private long docs;
    private long chunks;
    private String updated;
    private List<String> authAgents;

    private Dataset(String id, String name, String owner, String description, String embeddingModel,
                    String indexType, String metric, String parser, String chunkStrategy, int chunkSize,
                    int overlap, String retrievalMode, int topK, double threshold, boolean rerank,
                    boolean needsReindex, String status, long docs, long chunks, String updated,
                    List<String> authAgents) {
        this.id = id;
        this.name = name;
        this.owner = owner;
        this.description = description;
        this.embeddingModel = embeddingModel;
        this.indexType = indexType;
        this.metric = metric;
        this.parser = parser;
        this.chunkStrategy = chunkStrategy;
        this.chunkSize = chunkSize;
        this.overlap = overlap;
        this.retrievalMode = retrievalMode;
        this.topK = topK;
        this.threshold = threshold;
        this.rerank = rerank;
        this.needsReindex = needsReindex;
        this.status = status;
        this.docs = docs;
        this.chunks = chunks;
        this.updated = updated;
        this.authAgents = new ArrayList<>(authAgents);
    }

    public static Dataset restore(String id, String name, String owner, String description,
                                  String embeddingModel, String indexType, String metric,
                                  String parser, String chunkStrategy, int chunkSize, int overlap,
                                  String retrievalMode, int topK, double threshold, boolean rerank,
                                  boolean needsReindex, String status, long docs, long chunks,
                                  String updated, List<String> authAgents) {
        return new Dataset(id, name, owner, description, embeddingModel, indexType, metric, parser,
                chunkStrategy, chunkSize, overlap, retrievalMode, topK, threshold, rerank,
                needsReindex, status, docs, chunks, updated, authAgents);
    }

    public static Dataset create(String id, String name, String owner, String description,
                                 String embeddingModel, String indexType, String retrievalMode) {
        return new Dataset(id, name, owner, description, embeddingModel, indexType, "COSINE",
                "默认解析", "标题层级", 512, 64, retrievalMode, 4, 0.8, true,
                false, "empty", 0, 0, "刚刚", List.of());
    }

    public void updateBasic(String name, String owner, String description) {
        if (name != null && !name.isBlank()) this.name = name;
        if (owner != null && !owner.isBlank()) this.owner = owner;
        if (description != null) this.description = description;
        this.updated = "刚刚";
    }

    public void updateConfig(String embeddingModel, String indexType, String metric, String parser,
                             String chunkStrategy, Integer chunkSize, Integer overlap,
                             String retrievalMode, Integer topK, Double threshold, Boolean rerank,
                             List<String> authAgents, Boolean needsReindex) {
        boolean indexChanged = false;
        if (embeddingModel != null) { this.embeddingModel = embeddingModel; indexChanged = true; }
        if (indexType != null) { this.indexType = indexType; indexChanged = true; }
        if (metric != null) { this.metric = metric; indexChanged = true; }
        if (parser != null) { this.parser = parser; indexChanged = true; }
        if (chunkStrategy != null) { this.chunkStrategy = chunkStrategy; indexChanged = true; }
        if (chunkSize != null) { this.chunkSize = chunkSize; indexChanged = true; }
        if (overlap != null) { this.overlap = overlap; indexChanged = true; }
        if (retrievalMode != null) this.retrievalMode = retrievalMode;
        if (topK != null) this.topK = topK;
        if (threshold != null) this.threshold = threshold;
        if (rerank != null) this.rerank = rerank;
        if (authAgents != null) this.authAgents = new ArrayList<>(authAgents);
        if (needsReindex != null) this.needsReindex = needsReindex;
        if (indexChanged) this.needsReindex = true;
        this.updated = "刚刚";
    }

    public void markReindexed() {
        this.needsReindex = false;
        this.status = "ready";
        this.updated = "刚刚";
    }

    public void addDocument(long chunkCount) {
        this.docs++;
        this.chunks += chunkCount;
        this.status = "ready";
        this.updated = "刚刚";
    }

    public void removeDocument(long chunkCount) {
        this.docs = Math.max(0, this.docs - 1);
        this.chunks = Math.max(0, this.chunks - chunkCount);
        this.updated = "刚刚";
    }

    public String id() { return id; }
    public String name() { return name; }
    public String owner() { return owner; }
    public String description() { return description; }
    public String embeddingModel() { return embeddingModel; }
    public String indexType() { return indexType; }
    public String metric() { return metric; }
    public String parser() { return parser; }
    public String chunkStrategy() { return chunkStrategy; }
    public int chunkSize() { return chunkSize; }
    public int overlap() { return overlap; }
    public String retrievalMode() { return retrievalMode; }
    public int topK() { return topK; }
    public double threshold() { return threshold; }
    public boolean rerank() { return rerank; }
    public boolean needsReindex() { return needsReindex; }
    public String status() { return status; }
    public long docs() { return docs; }
    public long chunks() { return chunks; }
    public String updated() { return updated; }
    public List<String> authAgents() { return List.copyOf(authAgents); }
}
