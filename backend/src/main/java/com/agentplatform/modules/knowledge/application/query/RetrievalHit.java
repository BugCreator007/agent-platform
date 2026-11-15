package com.agentplatform.modules.knowledge.application.query;

/**
 * 单条检索命中结果。
 *
 * @param source 来源展示名（通常为文档名称，文档不存在时退化为文档 ID）
 * @param chunk  分块在文档内的顺序下标
 * @param score  相关度分数，向量模式下为余弦相似度，融合模式下为 RRF 分数，均被截断到 ≤ 0.99
 * @param text   命中的分块文本
 */
public record RetrievalHit(String source, int chunk, double score, String text) {
}
