package com.agentplatform.modules.knowledge.domain.model;

import java.util.List;

/**
 * 文档分块领域模型（不可变记录）。
 *
 * <p>表示文档切分后的一段文本及其向量表示，是向量检索与关键词检索的最小单位；
 * 同一分块同时写入数据库、向量库与关键词索引。</p>
 *
 * @param id         分块主键，格式 {@code chunk_<12位十六进制>} 或种子数据 ID
 * @param documentId 所属文档 ID
 * @param datasetId  所属数据集 ID
 * @param chunkIndex 分块在文档内的顺序下标，从 0 开始
 * @param content    分块文本内容
 * @param tokenCount 近似 token 数（当前按字符数折半估算）
 * @param embedding  文本向量；未计算或反序列化失败时为空列表
 */
public record DocumentChunk(String id, String documentId, String datasetId, int chunkIndex,
                            String content, int tokenCount, List<Double> embedding) {
}
