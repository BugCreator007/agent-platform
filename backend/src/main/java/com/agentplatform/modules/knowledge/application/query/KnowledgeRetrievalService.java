package com.agentplatform.modules.knowledge.application.query;

import com.agentplatform.modules.knowledge.domain.model.Dataset;
import com.agentplatform.modules.knowledge.domain.model.Document;
import com.agentplatform.modules.knowledge.domain.model.DocumentChunk;
import com.agentplatform.modules.knowledge.domain.repository.ChunkRepository;
import com.agentplatform.modules.knowledge.domain.repository.KnowledgeRepository;
import com.agentplatform.modules.knowledge.infrastructure.embedding.EmbeddingClient;
import com.agentplatform.modules.knowledge.infrastructure.retrieval.KeywordIndex;
import com.agentplatform.modules.knowledge.infrastructure.retrieval.VectorMatch;
import com.agentplatform.modules.knowledge.infrastructure.retrieval.VectorStore;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 知识库混合检索服务（应用层，命名接口 {@code api}）。
 *
 * <p>按数据集的 {@code retrievalMode} 执行向量检索、关键词检索或两者融合：</p>
 * <ul>
 *   <li>向量检索优先走 Milvus，无结果时回退到数据库内余弦相似度计算；</li>
 *   <li>关键词检索优先走 Elasticsearch，无结果时回退到数据库内二元组匹配打分；</li>
 *   <li>混合模式使用 RRF（Reciprocal Rank Fusion）对两路排名融合，无需对齐分数尺度。</li>
 * </ul>
 *
 * <p>向量召回会先过滤低于数据集阈值的命中；最终结果按融合分数降序截取 TopK。</p>
 */
@Service
public class KnowledgeRetrievalService {
    /** 分块仓储，用于获取候选文本与回退检索时的向量。 */
    private final ChunkRepository chunks;
    /** 知识库仓储，用于把文档 ID 映射为展示名称。 */
    private final KnowledgeRepository knowledge;
    /** 查询向量化客户端。 */
    private final EmbeddingClient embedding;
    /** 向量库（Milvus 或空实现）。 */
    private final VectorStore vectorStore;
    /** 关键词索引（Elasticsearch 或空实现）。 */
    private final KeywordIndex keywordIndex;

    /**
     * 构造检索服务。
     *
     * @param chunks       分块仓储
     * @param knowledge    知识库仓储
     * @param embedding    Embedding 客户端
     * @param vectorStore  向量库
     * @param keywordIndex 关键词索引
     */
    public KnowledgeRetrievalService(ChunkRepository chunks, KnowledgeRepository knowledge,
                                     EmbeddingClient embedding, VectorStore vectorStore,
                                     KeywordIndex keywordIndex) {
        this.chunks = chunks; this.knowledge = knowledge; this.embedding = embedding;
        this.vectorStore = vectorStore; this.keywordIndex = keywordIndex;
    }

    /**
     * 在指定数据集上执行一次检索。
     *
     * @param dataset 目标数据集，提供检索模式、TopK、阈值等配置
     * @param query   用户查询文本
     * @return 包含耗时、模式与实际命中列表的检索结果
     */
    public RetrievalResult retrieve(Dataset dataset, String query) {
        long start = System.currentTimeMillis();
        List<Double> vector = embedding.embed(query);
        List<DocumentChunk> all = chunks.findByDataset(dataset.id());
        Map<String, DocumentChunk> byId = new HashMap<>();
        all.forEach(c -> byId.put(c.id(), c));
        // scores 记录 RRF 融合分数；rawVector 保留原始余弦相似度用于最终展示
        Map<String, Double> scores = new LinkedHashMap<>();
        Map<String, Double> rawVector = new HashMap<>();

        if (!"keyword".equalsIgnoreCase(dataset.retrievalMode())) {
            // 多召回一些候选（TopK 的 3 倍且不少于 12），为融合和截断留出余量
            List<VectorMatch> vectorMatches = vectorStore.search(dataset.id(), vector, Math.max(dataset.topK() * 3, 12));
            // Milvus 未启用或调用失败时为空列表，此时回退到数据库内向量计算
            if (vectorMatches.isEmpty()) vectorMatches = dbVectorSearch(all, vector, Math.max(dataset.topK() * 3, 12));
            for (int i = 0; i < vectorMatches.size(); i++) {
                VectorMatch m = vectorMatches.get(i);
                DocumentChunk chunk = byId.get(m.id());
                if (chunk == null) continue;
                if (m.score() >= dataset.threshold()) {
                    rawVector.put(m.id(), m.score());
                    // RRF：每路按名次贡献 1/(60+rank)，常数 60 用于平滑头部排名的影响
                    scores.merge(m.id(), 1.0 / (60 + i + 1), Double::sum);
                }
            }
        }

        if (!"vector".equalsIgnoreCase(dataset.retrievalMode())) {
            List<VectorMatch> keywordMatches = keywordIndex.search(dataset.id(), query, Math.max(dataset.topK() * 3, 12));
            // Elasticsearch 未启用或调用失败时为空列表，此时回退到数据库内关键词匹配
            if (keywordMatches.isEmpty()) keywordMatches = dbKeywordSearch(all, query, Math.max(dataset.topK() * 3, 12));
            for (int i = 0; i < keywordMatches.size(); i++) {
                VectorMatch m = keywordMatches.get(i);
                if (!byId.containsKey(m.id())) continue;
                scores.merge(m.id(), 1.0 / (60 + i + 1), Double::sum);
            }
        }

        Map<String, Document> docs = new HashMap<>();
        knowledge.findDocuments(dataset.id()).forEach(d -> docs.put(d.id(), d));
        List<DocumentChunk> ranked = scores.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .map(e -> byId.get(e.getKey()))
                .filter(Objects::nonNull)
                .limit(dataset.topK())
                .toList();
        List<RetrievalHit> hits = new ArrayList<>();
        for (DocumentChunk chunk : ranked) {
            Document doc = docs.get(chunk.documentId());
            // 有原始向量分数时展示相似度，否则展示 RRF 融合分数；统一截断到 0.99 避免展示为 1.0
            double vectorScore = rawVector.getOrDefault(chunk.id(), scores.getOrDefault(chunk.id(), 0.0));
            hits.add(new RetrievalHit(doc == null ? chunk.documentId() : doc.name(), chunk.chunkIndex(),
                    Math.min(0.99, vectorScore), chunk.content()));
        }
        return new RetrievalResult(query, System.currentTimeMillis() - start,
                dataset.retrievalMode(), dataset.topK(), dataset.threshold(), hits);
    }

    /**
     * 数据库内向量检索回退：对数据集中所有分块计算余弦相似度并排序。
     *
     * @param chunks 数据集全部分块
     * @param query  查询向量
     * @param topK   返回数量上限
     * @return 按相似度降序的命中列表
     */
    private List<VectorMatch> dbVectorSearch(List<DocumentChunk> chunks, List<Double> query, int topK) {
        return chunks.stream().filter(c -> !c.embedding().isEmpty())
                .map(c -> new VectorMatch(c.id(), c.documentId(), c.chunkIndex(), c.content(), cosine(query, c.embedding())))
                .sorted((a, b) -> Double.compare(b.score(), a.score()))
                .limit(topK).toList();
    }

    /**
     * 数据库内关键词检索回退：按查询二元组的覆盖率打分。
     *
     * @param chunks 数据集全部分块
     * @param query  查询文本
     * @param topK   返回数量上限
     * @return 得分大于 0 的命中列表，按分数降序
     */
    private List<VectorMatch> dbKeywordSearch(List<DocumentChunk> chunks, String query, int topK) {
        String q = query == null ? "" : query.toLowerCase();
        return chunks.stream()
                .map(c -> new VectorMatch(c.id(), c.documentId(), c.chunkIndex(), c.content(), keywordScore(q, c.content())))
                .filter(m -> m.score() > 0)
                .sorted((a, b) -> Double.compare(b.score(), a.score()))
                .limit(topK).toList();
    }

    /**
     * 计算两个向量的余弦相似度；长度不一致时只比较公共前缀，零向量返回 0。
     *
     * @param a 向量 A
     * @param b 向量 B
     * @return 余弦相似度，范围 [-1, 1]
     */
    private double cosine(List<Double> a, List<Double> b) {
        int n = Math.min(a.size(), b.size());
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < n; i++) { dot += a.get(i) * b.get(i); na += a.get(i) * a.get(i); nb += b.get(i) * b.get(i); }
        return na == 0 || nb == 0 ? 0 : dot / Math.sqrt(na * nb);
    }

    /**
     * 简化的关键词打分：统计查询文本中所有相邻二元组在内容中的出现比例。
     *
     * <p>该实现不依赖分词器，对中文短查询较为有效；单字符查询直接返回 0。</p>
     *
     * @param query   小写化后的查询文本
     * @param content 分块文本
     * @return 命中二元组比例，范围 [0, 1]
     */
    private double keywordScore(String query, String content) {
        if (query.isBlank() || content == null) return 0;
        String text = content.toLowerCase();
        int hits = 0;
        // 以“相邻两字符”作为中文近似的 bigram 切分单位
        for (int i = 0; i < query.length() - 1; i++) {
            if (text.contains(query.substring(i, i + 2))) hits++;
        }
        return query.length() <= 1 ? 0 : (double) hits / Math.max(1, query.length() - 1);
    }
}
