package com.agentplatform.modules.knowledge.infrastructure.ingestion;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class TextChunker {
    public List<String> chunk(String text, int chunkSizeTokens) {
        return chunk(text, chunkSizeTokens, Math.max(0, chunkSizeTokens / 8));
    }

    public List<String> chunk(String text, int chunkSizeTokens, int overlapTokens) {
        if (text == null || text.isBlank()) return List.of();
        int limit = Math.max(128, chunkSizeTokens * 2);
        int overlap = Math.min(limit / 2, Math.max(0, overlapTokens * 2));
        String normalized = text.replace("\r\n", "\n").replaceAll("[ \\t]+", " ").trim();
        List<String> paragraphs = new ArrayList<>();
        for (String p : normalized.split("\\n{2,}")) {
            if (!p.isBlank()) paragraphs.add(p.trim());
        }
        if (paragraphs.isEmpty()) paragraphs.add(normalized);
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : paragraphs) {
            if (current.length() > 0 && current.length() + paragraph.length() + 1 > limit) {
                chunks.add(current.toString().trim());
                String tail = current.length() > overlap ? current.substring(current.length() - overlap) : current.toString();
                current = new StringBuilder(tail).append('\n');
            }
            if (paragraph.length() > limit) {
                for (int i = 0; i < paragraph.length(); i += Math.max(1, limit - overlap)) {
                    chunks.add(paragraph.substring(i, Math.min(paragraph.length(), i + limit)).trim());
                }
                current = new StringBuilder();
            } else {
                current.append(paragraph).append('\n');
            }
        }
        if (current.length() > 0) chunks.add(current.toString().trim());
        return chunks.stream().filter(s -> !s.isBlank()).toList();
    }
}
