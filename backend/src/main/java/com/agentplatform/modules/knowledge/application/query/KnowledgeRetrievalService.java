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

@Service
public class KnowledgeRetrievalService {
    private final ChunkRepository chunks;
    private final KnowledgeRepository knowledge;
    private final EmbeddingClient embedding;
    private final VectorStore vectorStore;
    private final KeywordIndex keywordIndex;

    public KnowledgeRetrievalService(ChunkRepository chunks, KnowledgeRepository knowledge,
                                     EmbeddingClient embedding, VectorStore vectorStore,
                                     KeywordIndex keywordIndex) {
        this.chunks = chunks; this.knowledge = knowledge; this.embedding = embedding;
        this.vectorStore = vectorStore; this.keywordIndex = keywordIndex;
    }

    public RetrievalResult retrieve(Dataset dataset, String query) {
        long start = System.currentTimeMillis();
        List<Double> vector = embedding.embed(query);
        List<DocumentChunk> all = chunks.findByDataset(dataset.id());
        Map<String, DocumentChunk> byId = new HashMap<>();
        all.forEach(c -> byId.put(c.id(), c));
        Map<String, Double> scores = new LinkedHashMap<>();
        Map<String, Double> rawVector = new HashMap<>();

        if (!"keyword".equalsIgnoreCase(dataset.retrievalMode())) {
            List<VectorMatch> vectorMatches = vectorStore.search(dataset.id(), vector, Math.max(dataset.topK() * 3, 12));
            if (vectorMatches.isEmpty()) vectorMatches = dbVectorSearch(all, vector, Math.max(dataset.topK() * 3, 12));
            for (int i = 0; i < vectorMatches.size(); i++) {
                VectorMatch m = vectorMatches.get(i);
                DocumentChunk chunk = byId.get(m.id());
                if (chunk == null) continue;
                if (m.score() >= dataset.threshold()) {
                    rawVector.put(m.id(), m.score());
                    scores.merge(m.id(), 1.0 / (60 + i + 1), Double::sum);
                }
            }
        }

        if (!"vector".equalsIgnoreCase(dataset.retrievalMode())) {
            List<VectorMatch> keywordMatches = keywordIndex.search(dataset.id(), query, Math.max(dataset.topK() * 3, 12));
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
            double vectorScore = rawVector.getOrDefault(chunk.id(), scores.getOrDefault(chunk.id(), 0.0));
            hits.add(new RetrievalHit(doc == null ? chunk.documentId() : doc.name(), chunk.chunkIndex(),
                    Math.min(0.99, vectorScore), chunk.content()));
        }
        return new RetrievalResult(query, System.currentTimeMillis() - start,
                dataset.retrievalMode(), dataset.topK(), dataset.threshold(), hits);
    }

    private List<VectorMatch> dbVectorSearch(List<DocumentChunk> chunks, List<Double> query, int topK) {
        return chunks.stream().filter(c -> !c.embedding().isEmpty())
                .map(c -> new VectorMatch(c.id(), c.documentId(), c.chunkIndex(), c.content(), cosine(query, c.embedding())))
                .sorted((a, b) -> Double.compare(b.score(), a.score()))
                .limit(topK).toList();
    }

    private List<VectorMatch> dbKeywordSearch(List<DocumentChunk> chunks, String query, int topK) {
        String q = query == null ? "" : query.toLowerCase();
        return chunks.stream()
                .map(c -> new VectorMatch(c.id(), c.documentId(), c.chunkIndex(), c.content(), keywordScore(q, c.content())))
                .filter(m -> m.score() > 0)
                .sorted((a, b) -> Double.compare(b.score(), a.score()))
                .limit(topK).toList();
    }

    private double cosine(List<Double> a, List<Double> b) {
        int n = Math.min(a.size(), b.size());
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < n; i++) { dot += a.get(i) * b.get(i); na += a.get(i) * a.get(i); nb += b.get(i) * b.get(i); }
        return na == 0 || nb == 0 ? 0 : dot / Math.sqrt(na * nb);
    }

    private double keywordScore(String query, String content) {
        if (query.isBlank() || content == null) return 0;
        String text = content.toLowerCase();
        int hits = 0;
        for (int i = 0; i < query.length() - 1; i++) {
            if (text.contains(query.substring(i, i + 2))) hits++;
        }
        return query.length() <= 1 ? 0 : (double) hits / Math.max(1, query.length() - 1);
    }
}
