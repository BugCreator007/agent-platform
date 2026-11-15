package com.agentplatform.modules.agent.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import com.agentplatform.modules.agent.infrastructure.persistence.entity.AgentEntity;

/**
 * {@code agents} 表的 MyBatis-Plus Mapper（infrastructure 层）。
 * <p>继承 {@link BaseMapper} 获得基础 CRUD，并补充按业务编码查询；
 * 对应 SQL 可通过注解或 resources 下的 XML 提供。</p>
 */
public interface AgentMapper extends BaseMapper<AgentEntity> {
    /**
     * 按业务编码查询智能体。
     *
     * @param code 业务编码
     * @return 匹配的实体，不存在时返回 null
     */
    AgentEntity findByCode(@Param("code") String code);
}
