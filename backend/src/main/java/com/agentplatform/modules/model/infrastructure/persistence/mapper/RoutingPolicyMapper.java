package com.agentplatform.modules.model.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import com.agentplatform.modules.model.infrastructure.persistence.entity.RoutingPolicyEntity;

/**
 * 路由策略表 MyBatis-Plus Mapper（infrastructure 层），直接复用 {@link BaseMapper} 的 CRUD 能力。
 */
public interface RoutingPolicyMapper extends BaseMapper<RoutingPolicyEntity> {
}
