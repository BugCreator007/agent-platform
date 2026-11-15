package com.agentplatform.modules.knowledge.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import com.agentplatform.modules.knowledge.infrastructure.persistence.entity.DocumentEntity;
import java.util.List;

/**
 * 文档 MyBatis-Plus Mapper。
 *
 * <p>自定义 SQL 定义在 {@code resources/mapper/DocumentMapper.xml}，用于按数据集查询文档。</p>
 */
public interface DocumentMapper extends BaseMapper<DocumentEntity> {
    /**
     * 查询指定数据集下的全部文档。
     *
     * @param datasetId 数据集 ID
     * @return 文档实体列表
     */
    List<DocumentEntity> findByDatasetId(@Param("datasetId") String datasetId);

    /**
     * 按数据集与文档 ID 删除文档，防止误删其他数据集下的同 ID 记录。
     *
     * @param datasetId 数据集 ID
     * @param id        文档 ID
     * @return 删除行数
     */
    int deleteByDatasetIdAndId(@Param("datasetId") String datasetId, @Param("id") String id);
}
