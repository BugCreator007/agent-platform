package com.agentplatform.modules.knowledge.infrastructure.persistence.repository;

import com.agentplatform.modules.knowledge.infrastructure.persistence.mapper.DocumentChunkMapper;

import com.agentplatform.modules.knowledge.domain.model.DocumentChunk;
import com.agentplatform.modules.knowledge.domain.repository.ChunkRepository;
import com.agentplatform.modules.knowledge.infrastructure.persistence.entity.DocumentChunkEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 文档分块仓储适配器（基础设施层），实现领域端口 {@link ChunkRepository}。
 *
 * <p>负责领域模型与 `document_chunks` 表之间的转换，其中向量序列化为 JSON 文本存储；
 * 反序列化失败时降级为空向量，避免单条脏数据影响整批查询。</p>
 */
@Repository
public class DocumentChunkRepository implements ChunkRepository {
    /** 专用于向量 JSON 序列化/反序列化的 ObjectMapper。 */
    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** MyBatis-Plus 分块 Mapper。 */
    private final DocumentChunkMapper mapper;

    /**
     * @param mapper 分块 Mapper
     */
    public DocumentChunkRepository(DocumentChunkMapper mapper) { this.mapper = mapper; }

    /**
     * 批量保存（存在则更新）分块，向量以 JSON 文本落库。
     *
     * @param chunks 分块领域模型列表
     * @throws IllegalArgumentException 向量序列化失败时抛出
     */
    @Override
    public void saveAll(List<DocumentChunk> chunks) {
        // 注意：这里逐条 insertOrUpdate，未包在显式事务中；摄取流程对部分写入的容忍度较高
        chunks.forEach(c -> {
            try {
                mapper.insertOrUpdate(new DocumentChunkEntity(c.id(), c.documentId(), c.datasetId(), c.chunkIndex(),
                        c.content(), c.tokenCount(), MAPPER.writeValueAsString(c.embedding()), null));
            } catch (Exception e) { throw new IllegalArgumentException("embedding serialize failed", e); }
        });
    }

    /**
     * 查询数据集全部分块。
     *
     * @param datasetId 数据集 ID
     * @return 分块领域模型列表
     */
    @Override public List<DocumentChunk> findByDataset(String datasetId) {
        return mapper.findByDatasetId(datasetId).stream().map(this::toDomain).toList();
    }
    /**
     * 查询文档全部分块。
     *
     * @param documentId 文档 ID
     * @return 分块领域模型列表
     */
    @Override public List<DocumentChunk> findByDocument(String documentId) {
        return mapper.findByDocumentId(documentId).stream().map(this::toDomain).toList();
    }
    /**
     * 删除文档全部分块；单条 SQL 自带原子性，事务注解用于在更大调用链中参与事务。
     *
     * @param documentId 文档 ID
     */
    @Override @Transactional public void deleteByDocument(String documentId) { mapper.deleteByDocumentId(documentId); }

    /**
     * 将持久化实体转换为领域模型；向量 JSON 缺失或损坏时使用空列表。
     *
     * @param e 分块实体
     * @return 分块领域模型
     */
    private DocumentChunk toDomain(DocumentChunkEntity e) {
        try {
            List<Double> embedding = e.getEmbeddingJson() == null || e.getEmbeddingJson().isBlank()
                    ? List.of() : MAPPER.readValue(e.getEmbeddingJson(), new TypeReference<>() {});
            return new DocumentChunk(e.getId(), e.getDocumentId(), e.getDatasetId(), e.getChunkIndex(),
                    e.getContent(), e.getTokenCount(), embedding);
        } catch (Exception ex) { return new DocumentChunk(e.getId(), e.getDocumentId(), e.getDatasetId(),
                e.getChunkIndex(), e.getContent(), e.getTokenCount(), List.of()); }
    }
}
