package com.agentplatform.modules.knowledge.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import com.agentplatform.modules.knowledge.infrastructure.persistence.entity.DocumentChunkEntity;
import java.util.List;

/**
 * 文档分块 MyBatis-Plus Mapper。
 *
 * <p>自定义 SQL 定义在 {@code resources/mapper/DocumentChunkMapper.xml}，
 * 用于按数据集/文档查询与按文档批量删除分块。</p>
 */
public interface DocumentChunkMapper extends BaseMapper<DocumentChunkEntity> {
    /**
     * 查询指定数据集下的全部分块。
     *
     * @param datasetId 数据集 ID
     * @return 分块实体列表
     */
    List<DocumentChunkEntity> findByDatasetId(@Param("datasetId") String datasetId);

    /**
     * 查询指定文档下的全部分块。
     *
     * @param documentId 文档 ID
     * @return 分块实体列表
     */
    List<DocumentChunkEntity> findByDocumentId(@Param("documentId") String documentId);

    /**
     * 删除指定文档的全部分块。
     *
     * @param documentId 文档 ID
     * @return 删除行数
     */
    int deleteByDocumentId(@Param("documentId") String documentId);
}
