package com.agentplatform.modules.knowledge.infrastructure.retrieval;

import java.util.List;

/**
 * 向量库端口（基础设施层）。
 *
 * <p>定义分块向量的写入、按数据集相似度检索与按文档删除能力；
 * 实现包括 Milvus 与未启用向量库时的空实现。检索失败时由上层回退到数据库内向量计算。</p>
 */
public interface VectorStore {
    /**
     * 写入/覆盖分块向量。
     *
     * @param records 待写入记录（含向量）
     */
    void upsert(List<VectorRecord> records);

    /**
     * 在指定数据集中执行向量相似度检索。
     *
     * @param datasetId 数据集 ID
     * @param vector    查询向量
     * @param topK      返回数量上限
     * @return 命中列表，按相似度降序；不可用时返回空列表
     */
    List<VectorMatch> search(String datasetId, List<Double> vector, int topK);

    /**
     * 删除指定文档的全部向量。
     *
     * @param documentId 文档 ID
     */
    void deleteByDocument(String documentId);
}
