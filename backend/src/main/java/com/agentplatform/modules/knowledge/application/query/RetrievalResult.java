package com.agentplatform.modules.knowledge.application.query;

import java.util.List;

/**
 * 一次检索的完整结果。
 *
 * @param query     原始查询文本
 * @param ms        检索耗时（毫秒）
 * @param mode      实际使用的检索模式（vector/keyword/hybrid）
 * @param topK      请求的返回数量上限
 * @param threshold 向量相似度阈值
 * @param hits      按相关度降序排列的命中列表
 */
public record RetrievalResult(
        String query, long ms, String mode, int topK, double threshold, List<RetrievalHit> hits
) {
}
