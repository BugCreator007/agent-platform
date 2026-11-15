package com.agentplatform.modules.knowledge.infrastructure.persistence.repository;

import com.agentplatform.modules.knowledge.domain.model.Dataset;
import com.agentplatform.modules.knowledge.domain.model.Document;
import com.agentplatform.modules.knowledge.domain.repository.ChunkRepository;
import com.agentplatform.modules.knowledge.domain.repository.KnowledgeRepository;
import com.agentplatform.modules.knowledge.infrastructure.persistence.entity.DatasetEntity;
import com.agentplatform.modules.knowledge.infrastructure.persistence.entity.DocumentEntity;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 领域仓储端口 {@link KnowledgeRepository} 的 MyBatis 实现（基础设施层，主实现）。
 *
 * <p>聚合 {@link DatasetRepository}、{@link DocumentRepository} 与 {@link ChunkRepository}，
 * 在领域对象与持久化实体之间转换。删除文档时在同一事务内先删分块再删文档，
 * 保证不残留孤儿分块。</p>
 */
@Primary
@Repository
public class MybatisKnowledgeRepository implements KnowledgeRepository {
    /** 数据集实体仓储。 */
    private final DatasetRepository datasets;
    /** 文档实体仓储。 */
    private final DocumentRepository documents;
    /** 分块仓储（领域端口）。 */
    private final ChunkRepository chunks;

    /**
     * 构造仓储适配器。
     *
     * @param datasets  数据集实体仓储
     * @param documents 文档实体仓储
     * @param chunks    分块仓储
     */
    public MybatisKnowledgeRepository(DatasetRepository datasets, DocumentRepository documents,
                                      ChunkRepository chunks) {
        this.datasets = datasets; this.documents = documents; this.chunks = chunks;
    }

    /**
     * {@inheritDoc}
     */
    @Override public List<Dataset> findAllDatasets() { return datasets.findAll().stream().map(DatasetEntity::toDomain).toList(); }
    /**
     * {@inheritDoc}
     */
    @Override public Optional<Dataset> findDataset(String id) { return datasets.findById(id).map(DatasetEntity::toDomain); }
    /**
     * {@inheritDoc}
     */
    @Override public void saveDataset(Dataset dataset) { datasets.save(DatasetEntity.from(dataset)); }
    /**
     * {@inheritDoc}
     */
    @Override public List<Document> findDocuments(String datasetId) {
        return documents.findByDataset(datasetId).stream().map(DocumentEntity::toDomain).toList();
    }
    /**
     * {@inheritDoc}
     */
    @Override public Optional<Document> findDocument(String id) { return documents.findById(id).map(DocumentEntity::toDomain); }
    /**
     * {@inheritDoc}
     */
    @Override public void saveDocument(Document document) { documents.save(DocumentEntity.from(document)); }
    /**
     * {@inheritDoc}
     *
     * <p>事务保证“分块删除 + 文档删除”要么同时成功、要么同时回滚。</p>
     */
    @Override @Transactional
    public void deleteDocument(String id) {
        chunks.deleteByDocument(id);
        documents.deleteById(id);
    }
}
