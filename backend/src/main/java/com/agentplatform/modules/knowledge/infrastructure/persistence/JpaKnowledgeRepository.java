package com.agentplatform.modules.knowledge.infrastructure.persistence;

import com.agentplatform.modules.knowledge.domain.model.Dataset;
import com.agentplatform.modules.knowledge.domain.model.Document;
import com.agentplatform.modules.knowledge.domain.repository.KnowledgeRepository;
import com.agentplatform.modules.knowledge.infrastructure.persistence.entity.DatasetEntity;
import com.agentplatform.modules.knowledge.infrastructure.persistence.entity.DocumentEntity;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Primary
@Repository
public class JpaKnowledgeRepository implements KnowledgeRepository {
    private final DatasetJpaRepository datasets;
    private final DocumentJpaRepository documents;
    private final DocumentChunkJpaRepository chunks;

    public JpaKnowledgeRepository(DatasetJpaRepository datasets, DocumentJpaRepository documents,
                                  DocumentChunkJpaRepository chunks) {
        this.datasets = datasets; this.documents = documents; this.chunks = chunks;
    }

    @Override public List<Dataset> findAllDatasets() { return datasets.findAll().stream().map(DatasetEntity::toDomain).toList(); }
    @Override public Optional<Dataset> findDataset(String id) { return datasets.findById(id).map(DatasetEntity::toDomain); }
    @Override public void saveDataset(Dataset dataset) { datasets.save(DatasetEntity.from(dataset)); }
    @Override public List<Document> findDocuments(String datasetId) {
        return documents.findByDatasetId(datasetId).stream().map(DocumentEntity::toDomain).toList();
    }
    @Override public Optional<Document> findDocument(String id) { return documents.findById(id).map(DocumentEntity::toDomain); }
    @Override public void saveDocument(Document document) { documents.save(DocumentEntity.from(document)); }
    @Override @Transactional
    public void deleteDocument(String id) {
        chunks.deleteByDocumentId(id);
        documents.deleteById(id);
    }
}
