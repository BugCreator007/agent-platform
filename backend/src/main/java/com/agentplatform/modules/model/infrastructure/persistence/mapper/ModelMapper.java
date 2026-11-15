package com.agentplatform.modules.model.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import com.agentplatform.modules.model.infrastructure.persistence.entity.ModelEntity;

/**
 * 模型表 MyBatis-Plus Mapper（infrastructure 层）。
 *
 * <p>基础 CRUD 由 {@link BaseMapper} 提供；额外声明按厂商批量删除。
 * 自定义 SQL 位于 {@code resources/mapper/ModelEntityMapper.xml}。</p>
 */
public interface ModelMapper extends BaseMapper<ModelEntity> {
    /**
     * 删除指定厂商下的全部模型。
     *
     * @param providerId 厂商 ID
     * @return 受影响行数
     */
    int deleteByProviderId(@Param("providerId") String providerId);
}
