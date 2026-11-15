package com.agentplatform.modules.knowledge.infrastructure.persistence.repository;

import com.agentplatform.modules.knowledge.infrastructure.persistence.mapper.DocumentMapper;

import com.agentplatform.modules.knowledge.infrastructure.persistence.entity.DocumentEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 文档仓储适配器（基础设施层）。
 *
 * <p>包装 {@link DocumentMapper}，提供实体级别的文档查询、保存与删除。</p>
 */
@Repository
public class DocumentRepository {
    /** MyBatis-Plus 文档 Mapper。 */
    private final DocumentMapper mapper;

    /**
     * @param mapper 文档 Mapper
     */
    public DocumentRepository(DocumentMapper mapper) { this.mapper = mapper; }

    /**
     * 查询指定数据集下的全部文档。
     *
     * @param datasetId 数据集 ID
     * @return 文档实体列表
     */
    public List<DocumentEntity> findByDataset(String datasetId) { return mapper.findByDatasetId(datasetId); }
    /**
     * 按主键查询文档。
     *
     * @param id 文档 ID
     * @return 文档实体，不存在时为空
     */
    public Optional<DocumentEntity> findById(String id) { return Optional.ofNullable(mapper.selectById(id)); }
    /**
     * 保存（存在则更新）文档。
     *
     * @param document 文档实体
     */
    public void save(DocumentEntity document) { mapper.insertOrUpdate(document); }
    /**
     * 按主键删除文档（分块级联删除由上层仓储负责）。
     *
     * @param id 文档 ID
     */
    public void deleteById(String id) { mapper.deleteById(id); }
}
