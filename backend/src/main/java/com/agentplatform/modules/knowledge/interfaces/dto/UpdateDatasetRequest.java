package com.agentplatform.modules.knowledge.interfaces.dto;

import java.util.List;

/**
 * 更新数据集的请求 DTO；所有字段均可选，null 表示不修改。
 *
 * <p>影响索引产物的配置（模型、索引类型、度量、解析器、切分参数）变化后，
 * 领域层会自动置位重建标记。</p>
 *
 * @param name           数据集名称
 * @param owner          归属方/负责人
 * @param description    描述
 * @param embeddingModel Embedding 模型标识
 * @param indexType      向量索引类型
 * @param metric         相似度度量方式
 * @param parser         文档解析器
 * @param chunkStrategy  分块策略
 * @param chunkSize      分块大小
 * @param overlap        分块重叠大小
 * @param retrievalMode  检索模式
 * @param topK           检索返回数量
 * @param threshold      向量相似度阈值
 * @param rerank         是否启用重排
 * @param authAgents     授权智能体 ID 列表
 * @param needsReindex   显式指定是否需要重建索引
 */
public record UpdateDatasetRequest(
        String name, String owner, String description,
        String embeddingModel, String indexType, String metric, String parser,
        String chunkStrategy, Integer chunkSize, Integer overlap,
        String retrievalMode, Integer topK, Double threshold, Boolean rerank,
        List<String> authAgents, Boolean needsReindex
) {
}
