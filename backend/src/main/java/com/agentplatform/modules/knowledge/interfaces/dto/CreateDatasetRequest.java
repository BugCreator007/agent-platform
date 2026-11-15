package com.agentplatform.modules.knowledge.interfaces.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 创建数据集的请求 DTO。
 *
 * @param name           数据集名称，必填
 * @param owner          归属方/负责人，可选，默认内置负责人
 * @param description    描述，可选
 * @param embeddingModel Embedding 模型标识，可选，默认 bge-m3
 * @param indexType      向量索引类型，可选，默认 HNSW
 * @param retrievalMode  检索模式（vector/keyword/hybrid），可选，默认 hybrid
 */
public record CreateDatasetRequest(
        @NotBlank String name,
        String owner,
        String description,
        String embeddingModel,
        String indexType,
        String retrievalMode
) {
}
