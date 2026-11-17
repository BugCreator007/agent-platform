package com.agentplatform.modules.knowledge.domain.service;

import java.util.List;

/**
 * 文本向量化客户端端口（基础设施层）。
 *
 * <p>提供单条与批量文本的 Embedding 能力；实现包括 OpenAI 兼容接口客户端
 * 与无外部依赖的本地哈希向量客户端。</p>
 */
public interface EmbeddingClient {
    /**
     * 将单条文本转换为向量。
     *
     * @param text 待向量化文本
     * @return 向量（维度由 {@link #dimension()} 决定）
     */
    List<Double> embed(String text);

    /**
     * 批量向量化，默认逐条调用 {@link #embed(String)}。
     *
     * @param texts 文本列表
     * @return 与输入顺序一致的向量列表
     */
    default List<List<Double>> embedAll(List<String> texts) { return texts.stream().map(this::embed).toList(); }

    /**
     * 返回向量维度。
     *
     * @return 维度
     */
    int dimension();
}
