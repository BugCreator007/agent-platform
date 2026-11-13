package com.agentplatform.modules.knowledge.infrastructure.embedding;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

@Component
public class HashEmbeddingClient implements EmbeddingClient {
    private final int dimension;
    public HashEmbeddingClient(@Value("${app.ai.embedding.dimension:384}") int dimension) {
        this.dimension = dimension;
    }
    @Override public int dimension() { return dimension; }
    @Override
    public List<Double> embed(String text) {
        double[] vec = new double[dimension];
        if (text != null) {
            String normalized = text.toLowerCase().replaceAll("\\s+", " ").trim();
            for (int i = 0; i < normalized.length(); i++) {
                int end = Math.min(normalized.length(), i + 2);
                String gram = normalized.substring(i, end);
                int idx = Math.floorMod(hash(gram), dimension);
                vec[idx] += 1.0;
            }
        }
        double norm = 0;
        for (double v : vec) norm += v * v;
        norm = Math.sqrt(norm);
        List<Double> result = new ArrayList<>(dimension);
        for (double v : vec) result.add(norm == 0 ? 0.0 : v / norm);
        return result;
    }
    private int hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return ((digest[0] & 0xff) << 24) | ((digest[1] & 0xff) << 16) | ((digest[2] & 0xff) << 8) | (digest[3] & 0xff);
        } catch (Exception e) { return value.hashCode(); }
    }
}
