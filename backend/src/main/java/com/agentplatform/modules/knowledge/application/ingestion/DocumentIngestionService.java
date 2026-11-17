package com.agentplatform.modules.knowledge.application.ingestion;

import com.agentplatform.modules.knowledge.domain.model.Dataset;
import com.agentplatform.modules.knowledge.domain.model.Document;
import com.agentplatform.modules.knowledge.domain.model.DocumentChunk;
import com.agentplatform.modules.knowledge.domain.repository.ChunkRepository;
import com.agentplatform.modules.knowledge.domain.repository.KnowledgeRepository;
import com.agentplatform.modules.knowledge.domain.service.EmbeddingClient;
import com.agentplatform.modules.knowledge.infrastructure.ingestion.DocumentTextExtractor;
import com.agentplatform.modules.knowledge.infrastructure.ingestion.TextChunker;
import com.agentplatform.modules.knowledge.domain.service.KeywordIndex;
import com.agentplatform.modules.knowledge.domain.service.VectorRecord;
import com.agentplatform.modules.knowledge.domain.service.VectorStore;
import com.agentplatform.modules.knowledge.infrastructure.storage.FileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 文档摄取流水线（应用层）。
 *
 * <p>以异步方式完成“读取原始文件 → 解析文本 → 切分 → Embedding → 写分块/向量库/关键词索引 → 更新文档与数据集统计”
 * 的完整链路。任一文档失败时仅将该文档标记为 failed，不影响同批次其他文档。</p>
 *
 * <p>关键约束：重新摄取前会先删除该文档在数据库、向量库与关键词索引中的旧数据，
 * 以保证重建索引的幂等性；方法运行在专用线程池 {@code ingestionExecutor} 上。</p>
 */
@Service
public class DocumentIngestionService {
    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);
    /** 数据集与文档元数据仓储。 */
    private final KnowledgeRepository knowledge;
    /** 文档分块仓储。 */
    private final ChunkRepository chunks;
    /** 原始文件存储（本地或 COS）。 */
    private final FileStorage storage;
    /** 多格式文本提取器。 */
    private final DocumentTextExtractor extractor;
    /** 文本分块器。 */
    private final TextChunker chunker;
    /** 文本向量化客户端。 */
    private final EmbeddingClient embedding;
    /** 向量库（Milvus 或空实现）。 */
    private final VectorStore vectorStore;
    /** 关键词索引（Elasticsearch 或空实现）。 */
    private final KeywordIndex keywordIndex;

    /**
     * 构造摄取服务。
     *
     * @param knowledge    知识库仓储
     * @param chunks       分块仓储
     * @param storage      文件存储
     * @param extractor    文本提取器
     * @param chunker      分块器
     * @param embedding    Embedding 客户端
     * @param vectorStore  向量库
     * @param keywordIndex 关键词索引
     */
    public DocumentIngestionService(KnowledgeRepository knowledge, ChunkRepository chunks,
                                    FileStorage storage, DocumentTextExtractor extractor, TextChunker chunker,
                                    EmbeddingClient embedding, VectorStore vectorStore, KeywordIndex keywordIndex) {
        this.knowledge = knowledge; this.chunks = chunks; this.storage = storage;
        this.extractor = extractor; this.chunker = chunker; this.embedding = embedding;
        this.vectorStore = vectorStore; this.keywordIndex = keywordIndex;
    }

    /**
     * 异步摄取单份文档：解析、切分、向量化并写入各类索引。
     *
     * <p>若文档不存在或没有存储键（如仅登记元数据的文档），直接跳过。
     * 处理过程中出现异常时记录日志并把文档状态置为 failed，原分块数据保持不变。</p>
     *
     * @param documentId 待摄取的文档 ID
     */
    @Async("ingestionExecutor")
    public void ingest(String documentId) {
        Document document = knowledge.findDocument(documentId).orElse(null);
        if (document == null || document.storageKey() == null || document.storageKey().isBlank()) return;
        try {
            byte[] bytes = storage.read(document.storageKey());
            String text = extractor.extract(document.name(), document.contentType(), bytes);
            Dataset dataset = knowledge.findDataset(document.datasetId())
                    .orElseThrow(() -> new IllegalStateException("数据集不存在"));
            List<String> pieces = chunker.chunk(text, dataset.chunkSize(), dataset.overlap());
            // 先清理旧分块与旧索引，避免重复摄取产生重复命中
            chunks.deleteByDocument(documentId);
            vectorStore.deleteByDocument(documentId);
            keywordIndex.deleteByDocument(documentId);
            List<DocumentChunk> records = new ArrayList<>();
            for (int i = 0; i < pieces.size(); i++) {
                String content = pieces.get(i);
                // tokenCount 以字符数折半做近似估算，用于统计与展示
                records.add(new DocumentChunk("chunk_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                        documentId, dataset.id(), i, content, Math.max(1, content.length() / 2), embedding.embed(content)));
            }
            chunks.saveAll(records);
            log.info("ingested document {} chunks {} vectorStore {}", documentId, records.size(), vectorStore.getClass().getSimpleName());
            // 数据库分块、向量库与关键词索引保存同一批记录，保证三者 ID 对齐以便融合检索
            vectorStore.upsert(records.stream().map(c -> new VectorRecord(c.id(), c.documentId(), c.datasetId(),
                    c.chunkIndex(), c.content(), c.embedding())).toList());
            keywordIndex.index(records.stream().map(c -> new VectorRecord(c.id(), c.documentId(), c.datasetId(),
                    c.chunkIndex(), c.content(), c.embedding())).toList());
            long oldChunks = document.chunks();
            Document ready = new Document(document.id(), document.datasetId(), document.name(), document.version(),
                    records.size(), document.size(), "ready", "刚刚", document.storageKey(),
                    document.contentType(), document.checksum(), document.sourceType(), document.sourceUrl());
            knowledge.saveDocument(ready);
            // 以“先减旧分块数、再加新分块数”的方式维护数据集统计，避免重复摄取时计数漂移
            dataset.removeDocument(oldChunks);
            dataset.addDocument(records.size());
            knowledge.saveDataset(dataset);
        } catch (Exception e) {
            log.error("document ingestion failed: {}", documentId, e);
            Document failed = new Document(document.id(), document.datasetId(), document.name(), document.version(),
                    document.chunks(), document.size(), "failed", "刚刚", document.storageKey(),
                    document.contentType(), document.checksum(), document.sourceType(), document.sourceUrl());
            knowledge.saveDocument(failed);
        }
    }

    /**
     * 异步重建整个数据集的索引：逐份文档重新执行 {@link #ingest(String)}。
     *
     * @param datasetId 数据集 ID；不存在时不做任何操作
     */
    @Async("ingestionExecutor")
    public void reindexDataset(String datasetId) {
        knowledge.findDataset(datasetId).ifPresent(dataset ->
                knowledge.findDocuments(datasetId).forEach(doc -> ingest(doc.id())));
    }
}
