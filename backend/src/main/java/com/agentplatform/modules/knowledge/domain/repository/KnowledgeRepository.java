package com.agentplatform.modules.knowledge.domain.repository;

import com.agentplatform.modules.knowledge.domain.model.Dataset;
import com.agentplatform.modules.knowledge.domain.model.Document;

import java.util.List;
import java.util.Optional;

public interface KnowledgeRepository {
    List<Dataset> findAllDatasets();
    Optional<Dataset> findDataset(String id);
    void saveDataset(Dataset dataset);
    List<Document> findDocuments(String datasetId);
    Optional<Document> findDocument(String id);
    void saveDocument(Document document);
    void deleteDocument(String id);
}
