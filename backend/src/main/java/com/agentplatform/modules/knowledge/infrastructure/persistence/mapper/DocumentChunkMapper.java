package com.agentplatform.modules.knowledge.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import com.agentplatform.modules.knowledge.infrastructure.persistence.entity.DocumentChunkEntity;
import java.util.List;

public interface DocumentChunkMapper extends BaseMapper<DocumentChunkEntity> {
    List<DocumentChunkEntity> findByDatasetId(@Param("datasetId") String datasetId);

    List<DocumentChunkEntity> findByDocumentId(@Param("documentId") String documentId);

    int deleteByDocumentId(@Param("documentId") String documentId);
}
