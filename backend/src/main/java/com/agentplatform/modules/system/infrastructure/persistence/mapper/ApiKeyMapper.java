package com.agentplatform.modules.system.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import com.agentplatform.modules.system.infrastructure.persistence.entity.ApiKeyEntity;

/**
 * API Key MyBatis-Plus Mapper。
 *
 * <p>位于基础设施持久化层，继承 {@link BaseMapper} 获得标准 CRUD，
 * 当前无需自定义 SQL。</p>
 */
public interface ApiKeyMapper extends BaseMapper<ApiKeyEntity> {
}
