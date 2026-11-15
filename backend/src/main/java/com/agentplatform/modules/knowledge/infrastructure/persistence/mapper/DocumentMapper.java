package com.agentplatform.modules.knowledge.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import com.agentplatform.modules.knowledge.infrastructure.persistence.entity.DocumentEntity;
import java.util.List;

public interface DocumentMapper extends BaseMapper<DocumentEntity> {
    List<DocumentEntity> findByDatasetId(@Param("datasetId") String datasetId);

    int deleteByDatasetIdAndId(@Param("datasetId") String datasetId, @Param("id") String id);
}
