package com.agentplatform.modules.knowledge.infrastructure.retrieval;

import java.util.List;

/**
 * 待写入向量库/关键词索引的分块记录。
 *
 * <p>与 {@link VectorMatch} 的区别：本记录包含 {@code datasetId} 与 {@code embedding}，
 * 用于写入阶段；{@link VectorMatch} 用于检索结果返回。</p>
 *
 * @param id         分块 ID
 * @param documentId 所属文档 ID
 * @param datasetId  所属数据集 ID
 * @param chunkIndex 分块下标
 * @param content    分块文本
 * @param embedding  分块向量
 */
public record VectorRecord(String id, String documentId, String datasetId, int chunkIndex,
                           String content, List<Double> embedding) {
}
