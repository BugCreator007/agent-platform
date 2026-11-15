package com.agentplatform.modules.knowledge.domain.repository;

import com.agentplatform.modules.knowledge.domain.model.Dataset;
import com.agentplatform.modules.knowledge.domain.model.Document;

import java.util.List;
import java.util.Optional;

/**
 * 知识库聚合仓储端口（领域层）。
 *
 * <p>统一暴露数据集与文档的查询与保存能力。删除文档时需级联清理分块，
 * 该保证由实现（MyBatis 事务适配器）承担；种子数据的内存实现仅用于首次启动初始化。</p>
 */
public interface KnowledgeRepository {
    /**
     * 查询全部数据集。
     *
     * @return 数据集列表
     */
    List<Dataset> findAllDatasets();

    /**
     * 按 ID 查询数据集。
     *
     * @param id 数据集 ID
     * @return 数据集，不存在时为空
     */
    Optional<Dataset> findDataset(String id);

    /**
     * 保存（存在则更新）数据集。
     *
     * @param dataset 数据集聚合
     */
    void saveDataset(Dataset dataset);

    /**
     * 查询指定数据集下的全部文档。
     *
     * @param datasetId 数据集 ID
     * @return 文档列表
     */
    List<Document> findDocuments(String datasetId);

    /**
     * 按 ID 查询文档。
     *
     * @param id 文档 ID
     * @return 文档，不存在时为空
     */
    Optional<Document> findDocument(String id);

    /**
     * 保存（存在则更新）文档。
     *
     * @param document 文档模型
     */
    void saveDocument(Document document);

    /**
     * 删除文档，并级联删除其分块。
     *
     * @param id 文档 ID
     */
    void deleteDocument(String id);
}
