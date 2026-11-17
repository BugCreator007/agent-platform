package com.agentplatform.modules.knowledge.domain.service;

/**
 * 向量/关键词检索的单条命中。
 *
 * <p>同一记录同时用于两种检索来源：{@code score} 在向量检索中为相似度，在关键词检索中为相关度。</p>
 *
 * @param id         分块 ID
 * @param documentId 所属文档 ID
 * @param chunkIndex 分块下标
 * @param content    分块文本
 * @param score      检索得分
 */
public record VectorMatch(String id, String documentId, int chunkIndex, String content, double score) {
}
