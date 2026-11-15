package com.agentplatform.modules.knowledge.domain.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识库数据集聚合根（领域层）。
 *
 * <p>承载数据集的索引配置（Embedding 模型、索引类型、度量方式）、解析与切分配置、
 * 检索配置（模式、TopK、阈值、重排）、统计信息（文档数、分块数）与可访问的智能体列表。
 * 仅提供领域行为方法修改自身状态，外部通过 {@link #restore} 重建或 {@link #create} 新建。</p>
 *
 * <p>关键约束：任何影响索引产物的配置（模型、索引类型、度量、解析器、切分策略/大小/重叠）变更
 * 都会自动置位 {@code needsReindex}，提示调用方需要重建索引。</p>
 */
public class Dataset {
    /** 数据集主键，格式 {@code kb_<12位十六进制>}。 */
    private final String id;
    /** 数据集展示名称。 */
    private String name;
    /** 数据集归属方/负责人。 */
    private String owner;
    /** 数据集描述。 */
    private String description;
    /** 使用的 Embedding 模型标识，如 bge-m3、text-embedding-3-large。 */
    private String embeddingModel;
    /** 向量索引类型，如 HNSW、IVF_FLAT。 */
    private String indexType;
    /** 向量相似度度量方式，如 COSINE、IP、L2。 */
    private String metric;
    /** 文档解析器标识，如“默认解析”“表格增强”“OCR 增强”。 */
    private String parser;
    /** 分块策略，如“标题层级”“按段落”“固定长度”。 */
    private String chunkStrategy;
    /** 目标分块大小（以 token 近似计）。 */
    private int chunkSize;
    /** 相邻分块的重叠大小（token 近似计）。 */
    private int overlap;
    /** 检索模式：{@code vector}、{@code keyword} 或 {@code hybrid}。 */
    private String retrievalMode;
    /** 检索返回的最大命中数量。 */
    private int topK;
    /** 向量相似度阈值，低于该值的向量命中会被过滤。 */
    private double threshold;
    /** 是否启用重排（rerank）。 */
    private boolean rerank;
    /** 索引是否需要重建；配置变更后置位，重建完成后清除。 */
    private boolean needsReindex;
    /** 数据集状态，如 empty、indexing、ready。 */
    private String status;
    /** 当前数据集下的文档数量。 */
    private long docs;
    /** 当前数据集下的分块总数。 */
    private long chunks;
    /** 最近更新时间的人性化展示文本（如“刚刚”“昨天 17:10”）。 */
    private String updated;
    /** 被授权访问该数据集的智能体 ID 列表。 */
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

    /**
     * 全量重建数据集实例，供持久化层将数据库记录还原为领域对象。
     *
     * @param id             数据集主键
     * @param name           数据集名称
     * @param owner          归属方/负责人
     * @param description    描述
     * @param embeddingModel Embedding 模型标识
     * @param indexType      向量索引类型
     * @param metric         相似度度量方式
     * @param parser         文档解析器
     * @param chunkStrategy  分块策略
     * @param chunkSize      分块大小
     * @param overlap        分块重叠大小
     * @param retrievalMode  检索模式（vector/keyword/hybrid）
     * @param topK           检索返回数量
     * @param threshold      向量相似度阈值
     * @param rerank         是否启用重排
     * @param needsReindex   是否需要重建索引
     * @param status         数据集状态
     * @param docs           文档数量
     * @param chunks         分块总数
     * @param updated        更新时间展示文本
     * @param authAgents     授权的智能体 ID 列表
     * @return 还原后的数据集聚合
     */
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

    /**
     * 创建新数据集，填充默认索引与检索配置。
     *
     * @param id             数据集主键（由调用方生成）
     * @param name           数据集名称
     * @param owner          归属方/负责人
     * @param description    描述
     * @param embeddingModel Embedding 模型标识
     * @param indexType      向量索引类型
     * @param retrievalMode  检索模式（vector/keyword/hybrid）
     * @return 新建的数据集，状态为 empty，度量方式为 COSINE，默认切分 512/64、TopK 4、阈值 0.8、启用重排
     */
    public static Dataset create(String id, String name, String owner, String description,
                                 String embeddingModel, String indexType, String retrievalMode) {
        return new Dataset(id, name, owner, description, embeddingModel, indexType, "COSINE",
                "默认解析", "标题层级", 512, 64, retrievalMode, 4, 0.8, true,
                false, "empty", 0, 0, "刚刚", List.of());
    }

    /**
     * 更新数据集的基础展示信息；null 或空白的名称/归属方将被忽略，描述允许置空。
     *
     * @param name        新名称，可为 null（不修改）
     * @param owner       新归属方，可为 null（不修改）
     * @param description 新描述，可为 null（不修改）
     */
    public void updateBasic(String name, String owner, String description) {
        if (name != null && !name.isBlank()) this.name = name;
        if (owner != null && !owner.isBlank()) this.owner = owner;
        if (description != null) this.description = description;
        this.updated = "刚刚";
    }

    /**
     * 局部更新数据集的索引、解析、切分与检索配置；入参为 null 的字段保持原值。
     *
     * <p>影响索引产物的配置一旦变化，会自动将 {@code needsReindex} 置为 true；
     * 检索模式、TopK、阈值、重排等查询期配置变化不触发重建。</p>
     *
     * @param embeddingModel Embedding 模型标识，可为 null
     * @param indexType      向量索引类型，可为 null
     * @param metric         相似度度量方式，可为 null
     * @param parser         文档解析器，可为 null
     * @param chunkStrategy  分块策略，可为 null
     * @param chunkSize      分块大小，可为 null
     * @param overlap        分块重叠大小，可为 null
     * @param retrievalMode  检索模式，可为 null
     * @param topK           检索返回数量，可为 null
     * @param threshold      向量相似度阈值，可为 null
     * @param rerank         是否启用重排，可为 null
     * @param authAgents     授权智能体 ID 列表，可为 null
     * @param needsReindex   显式指定的重建标记，可为 null
     */
    public void updateConfig(String embeddingModel, String indexType, String metric, String parser,
                             String chunkStrategy, Integer chunkSize, Integer overlap,
                             String retrievalMode, Integer topK, Double threshold, Boolean rerank,
                             List<String> authAgents, Boolean needsReindex) {
        boolean indexChanged = false;
        // 模型、索引类型、度量、解析与切分参数决定索引产物，变更后必须重建索引
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

    /**
     * 标记索引已重建完成：清除重建标记并将状态置为 ready。
     */
    public void markReindexed() {
        this.needsReindex = false;
        this.status = "ready";
        this.updated = "刚刚";
    }

    /**
     * 登记新增文档对统计信息的影响。
     *
     * @param chunkCount 新文档产生的分块数量
     */
    public void addDocument(long chunkCount) {
        this.docs++;
        this.chunks += chunkCount;
        this.status = "ready";
        this.updated = "刚刚";
    }

    /**
     * 登记删除文档对统计信息的影响；计数不会低于 0。
     *
     * @param chunkCount 被删除文档对应的分块数量
     */
    public void removeDocument(long chunkCount) {
        this.docs = Math.max(0, this.docs - 1);
        this.chunks = Math.max(0, this.chunks - chunkCount);
        this.updated = "刚刚";
    }

    /**
     * @return 数据集主键
     */
    public String id() { return id; }
    /**
     * @return 数据集名称
     */
    public String name() { return name; }
    /**
     * @return 归属方/负责人
     */
    public String owner() { return owner; }
    /**
     * @return 数据集描述
     */
    public String description() { return description; }
    /**
     * @return Embedding 模型标识
     */
    public String embeddingModel() { return embeddingModel; }
    /**
     * @return 向量索引类型
     */
    public String indexType() { return indexType; }
    /**
     * @return 相似度度量方式
     */
    public String metric() { return metric; }
    /**
     * @return 文档解析器
     */
    public String parser() { return parser; }
    /**
     * @return 分块策略
     */
    public String chunkStrategy() { return chunkStrategy; }
    /**
     * @return 分块大小
     */
    public int chunkSize() { return chunkSize; }
    /**
     * @return 分块重叠大小
     */
    public int overlap() { return overlap; }
    /**
     * @return 检索模式（vector/keyword/hybrid）
     */
    public String retrievalMode() { return retrievalMode; }
    /**
     * @return 检索返回数量
     */
    public int topK() { return topK; }
    /**
     * @return 向量相似度阈值
     */
    public double threshold() { return threshold; }
    /**
     * @return 是否启用重排
     */
    public boolean rerank() { return rerank; }
    /**
     * @return 是否需要重建索引
     */
    public boolean needsReindex() { return needsReindex; }
    /**
     * @return 数据集状态
     */
    public String status() { return status; }
    /**
     * @return 文档数量
     */
    public long docs() { return docs; }
    /**
     * @return 分块总数
     */
    public long chunks() { return chunks; }
    /**
     * @return 更新时间展示文本
     */
    public String updated() { return updated; }
    /**
     * @return 授权智能体 ID 的不可变副本
     */
    public List<String> authAgents() { return List.copyOf(authAgents); }
}
