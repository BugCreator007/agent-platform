package com.agentplatform.modules.knowledge.domain.service;

import java.util.List;

/**
 * 关键词索引端口（基础设施层）。
 *
 * <p>定义分块的全文索引写入、按数据集检索与按文档删除能力；
 * 实现包括 Elasticsearch 与不启用检索时的空实现。</p>
 */
public interface KeywordIndex {
    /**
     * 写入/覆盖分块的全文索引。
     *
     * @param records 待索引记录
     */
    void index(List<VectorRecord> records);

    /**
     * 在指定数据集中按查询文本检索。
     *
     * @param datasetId 数据集 ID
     * @param query     查询文本
     * @param topK      返回数量上限
     * @return 命中列表，按相关度降序；不可用时返回空列表
     */
    List<VectorMatch> search(String datasetId, String query, int topK);

    /**
     * 删除指定文档的全部索引记录。
     *
     * @param documentId 文档 ID
     */
    void deleteByDocument(String documentId);
}
