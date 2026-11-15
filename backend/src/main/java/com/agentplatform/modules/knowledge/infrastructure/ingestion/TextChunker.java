package com.agentplatform.modules.knowledge.infrastructure.ingestion;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本分块器（基础设施层）。
 *
 * <p>先按空行切分段落，再在段落边界上累积到目标长度；超长段落按固定窗口滑动切分。
 * 相邻分块之间保留重叠文本，避免句子/语义被边界截断。</p>
 *
 * <p>说明：当前实现以字符数近似 token 数（分块上限 = {@code chunkSizeTokens * 2}），
 * 不依赖具体分词器，适用于中英文混合文本的演示场景。</p>
 */
@Component
public class TextChunker {
    /**
     * 使用默认重叠（分块大小的 1/8）进行切分。
     *
     * @param text            原始文本
     * @param chunkSizeTokens 目标分块大小（近似 token）
     * @return 分块列表
     */
    public List<String> chunk(String text, int chunkSizeTokens) {
        return chunk(text, chunkSizeTokens, Math.max(0, chunkSizeTokens / 8));
    }

    /**
     * 按段落与固定窗口切分文本，并保留相邻分块重叠。
     *
     * @param text            原始文本；空白文本返回空列表
     * @param chunkSizeTokens 目标分块大小（近似 token）
     * @param overlapTokens   相邻分块的重叠大小（近似 token）
     * @return 非空的分块列表
     */
    public List<String> chunk(String text, int chunkSizeTokens, int overlapTokens) {
        if (text == null || text.isBlank()) return List.of();
        // 字符数上限取 token 估算值的 2 倍；重叠不超过上限的一半，防止窗口无法前进
        int limit = Math.max(128, chunkSizeTokens * 2);
        int overlap = Math.min(limit / 2, Math.max(0, overlapTokens * 2));
        String normalized = text.replace("\r\n", "\n").replaceAll("[ \\t]+", " ").trim();
        List<String> paragraphs = new ArrayList<>();
        // 连续两个及以上换行视为段落边界
        for (String p : normalized.split("\\n{2,}")) {
            if (!p.isBlank()) paragraphs.add(p.trim());
        }
        if (paragraphs.isEmpty()) paragraphs.add(normalized);
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : paragraphs) {
            if (current.length() > 0 && current.length() + paragraph.length() + 1 > limit) {
                chunks.add(current.toString().trim());
                // 保留当前缓冲区末尾 overlap 个字符作为下一分块的开头，维持上下文连续
                String tail = current.length() > overlap ? current.substring(current.length() - overlap) : current.toString();
                current = new StringBuilder(tail).append('\n');
            }
            if (paragraph.length() > limit) {
                // 超长段落无法按段落边界切分，改用固定窗口滑动，步长为 limit - overlap
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
