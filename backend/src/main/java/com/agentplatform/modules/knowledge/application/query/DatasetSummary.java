package com.agentplatform.modules.knowledge.application.query;

import com.agentplatform.modules.knowledge.domain.model.Dataset;

import java.util.List;

/**
 * 数据集只读摘要视图模型，用于 REST 响应与跨模块查询。
 *
 * <p>字段与 {@link Dataset} 聚合一一对应，避免将领域对象直接暴露给接口层。</p>
 *
 * @param id             数据集主键
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
 * @param retrievalMode  检索模式（vector/keyword/hybrid）
 * @param topK           检索返回数量
 * @param threshold      向量相似度阈值
 * @param rerank         是否启用重排
 * @param needsReindex   是否需要重建索引
 * @param status         数据集状态
 * @param docs           文档数量
 * @param chunks         分块总数
 * @param updated        更新时间展示文本
 * @param authAgents     授权的智能体 ID 列表
 */
public record DatasetSummary(
        String id, String name, String owner, String description,
        String embeddingModel, String indexType, String metric, String parser,
        String chunkStrategy, int chunkSize, int overlap, String retrievalMode,
        int topK, double threshold, boolean rerank, boolean needsReindex,
        String status, long docs, long chunks, String updated, List<String> authAgents
) {
    /**
     * 由领域聚合转换为摘要视图。
     *
     * @param d 数据集聚合
     * @return 对应的摘要视图
     */
    public static DatasetSummary from(Dataset d) {
        return new DatasetSummary(d.id(), d.name(), d.owner(), d.description(), d.embeddingModel(),
                d.indexType(), d.metric(), d.parser(), d.chunkStrategy(), d.chunkSize(), d.overlap(),
                d.retrievalMode(), d.topK(), d.threshold(), d.rerank(), d.needsReindex(), d.status(),
                d.docs(), d.chunks(), d.updated(), d.authAgents());
    }
}
