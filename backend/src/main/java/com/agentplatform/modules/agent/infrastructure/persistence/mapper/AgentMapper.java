package com.agentplatform.modules.agent.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import com.agentplatform.modules.agent.infrastructure.persistence.entity.AgentEntity;

public interface AgentMapper extends BaseMapper<AgentEntity> {
    AgentEntity findByCode(@Param("code") String code);
}
