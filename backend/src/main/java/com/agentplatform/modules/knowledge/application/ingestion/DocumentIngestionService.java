package com.agentplatform.modules.knowledge.application.ingestion;

import com.agentplatform.modules.knowledge.domain.model.Dataset;
import com.agentplatform.modules.knowledge.domain.model.Document;
import com.agentplatform.modules.knowledge.domain.model.DocumentChunk;
import com.agentplatform.modules.knowledge.domain.repository.ChunkRepository;
import com.agentplatform.modules.knowledge.domain.repository.KnowledgeRepository;
import com.agentplatform.modules.knowledge.infrastructure.embedding.EmbeddingClient;
import com.agentplatform.modules.knowledge.infrastructure.ingestion.DocumentTextExtractor;
import com.agentplatform.modules.knowledge.infrastructure.ingestion.TextChunker;
import com.agentplatform.modules.knowledge.infrastructure.retrieval.KeywordIndex;
import com.agentplatform.modules.knowledge.infrastructure.retrieval.VectorRecord;
import com.agentplatform.modules.knowledge.infrastructure.retrieval.VectorStore;
import com.agentplatform.modules.knowledge.infrastructure.storage.FileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentIngestionService {
    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);
    private final KnowledgeRepository knowledge;
    private final ChunkRepository chunks;
    private final FileStorage storage;
    private final DocumentTextExtractor extractor;
    private final TextChunker chunker;
    private final EmbeddingClient embedding;
    private final VectorStore vectorStore;
    private final KeywordIndex keywordIndex;

    public DocumentIngestionService(KnowledgeRepository knowledge, ChunkRepository chunks,
                                    FileStorage storage, DocumentTextExtractor extractor, TextChunker chunker,
                                    EmbeddingClient embedding, VectorStore vectorStore, KeywordIndex keywordIndex) {
        this.knowledge = knowledge; this.chunks = chunks; this.storage = storage;
        this.extractor = extractor; this.chunker = chunker; this.embedding = embedding;
        this.vectorStore = vectorStore; this.keywordIndex = keywordIndex;
    }

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
            chunks.deleteByDocument(documentId);
            vectorStore.deleteByDocument(documentId);
            keywordIndex.deleteByDocument(documentId);
            List<DocumentChunk> records = new ArrayList<>();
            for (int i = 0; i < pieces.size(); i++) {
                String content = pieces.get(i);
                records.add(new DocumentChunk("chunk_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                        documentId, dataset.id(), i, content, Math.max(1, content.length() / 2), embedding.embed(content)));
            }
            chunks.saveAll(records);
            log.info("ingested document {} chunks {} vectorStore {}", documentId, records.size(), vectorStore.getClass().getSimpleName());
            vectorStore.upsert(records.stream().map(c -> new VectorRecord(c.id(), c.documentId(), c.datasetId(),
                    c.chunkIndex(), c.content(), c.embedding())).toList());
            keywordIndex.index(records.stream().map(c -> new VectorRecord(c.id(), c.documentId(), c.datasetId(),
                    c.chunkIndex(), c.content(), c.embedding())).toList());
            long oldChunks = document.chunks();
            Document ready = new Document(document.id(), document.datasetId(), document.name(), document.version(),
                    records.size(), document.size(), "ready", "刚刚", document.storageKey(),
                    document.contentType(), document.checksum(), document.sourceType(), document.sourceUrl());
            knowledge.saveDocument(ready);
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

    @Async("ingestionExecutor")
    public void reindexDataset(String datasetId) {
        knowledge.findDataset(datasetId).ifPresent(dataset ->
                knowledge.findDocuments(datasetId).forEach(doc -> ingest(doc.id())));
    }
}
