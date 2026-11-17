package com.agentplatform.modules.knowledge.application.query;

import java.util.List;

/**
 * 重排（Rerank）出站端口（应用层）。
 *
 * <p>知识库检索先用向量/关键词召回较多候选，再由外部 Cross-Encoder 模型对
 * “查询-分块”逐对打分并重新排序。接口放在应用层，具体 HTTP 实现位于
 * infrastructure，避免应用层直接依赖外部服务细节。</p>
 *
 * <p>未配置重排服务时 {@link #configured()} 返回 false，调用方应跳过重排并
 * 保持融合排序结果，保证系统在无重排 Key 时仍可运行。</p>
 */
public interface RerankClient {

    /**
     * 是否已配置可用的重排服务。
     *
     * @return true 表示可以调用 {@link #rerank(String, List, int)}
     */
    boolean configured();

    /**
     * 对候选文档按与查询的相关性重新排序。
     *
     * @param query     用户查询
     * @param documents 候选分块文本，顺序与调用方候选列表一致
     * @param topN      期望返回的最大条数
     * @return 重排结果（下标指向 {@code documents}）；未配置或调用失败时返回空列表
     */
    List<RerankHit> rerank(String query, List<String> documents, int topN);

    /**
     * 单条重排结果。
     *
     * @param index 对应 {@code documents} 中的下标
     * @param score 重排相关度分数，越高越相关
     */
    record RerankHit(int index, double score) {
    }
}
