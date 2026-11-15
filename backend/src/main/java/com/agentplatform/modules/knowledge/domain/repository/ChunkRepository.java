package com.agentplatform.modules.knowledge.domain.repository;

import com.agentplatform.modules.knowledge.domain.model.DocumentChunk;

import java.util.List;

/**
 * 文档分块仓储端口（领域层）。
 *
 * <p>定义分块的批量保存、按数据集/文档查询与按文档删除能力；
 * 具体实现由基础设施层的 MyBatis 适配器提供，领域与应用层不感知存储细节。</p>
 */
public interface ChunkRepository {
    /**
     * 批量保存（存在则更新）分块。
     *
     * @param chunks 待保存的分块列表
     */
    void saveAll(List<DocumentChunk> chunks);

    /**
     * 查询指定数据集下的全部分块。
     *
     * @param datasetId 数据集 ID
     * @return 该数据集的分块列表
     */
    List<DocumentChunk> findByDataset(String datasetId);

    /**
     * 查询指定文档下的全部分块。
     *
     * @param documentId 文档 ID
     * @return 该文档的分块列表
     */
    List<DocumentChunk> findByDocument(String documentId);

    /**
     * 删除指定文档的全部分块，用于重建索引前清理旧数据。
     *
     * @param documentId 文档 ID
     */
    void deleteByDocument(String documentId);
}
