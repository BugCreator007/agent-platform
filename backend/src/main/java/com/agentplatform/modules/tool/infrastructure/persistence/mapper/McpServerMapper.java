package com.agentplatform.modules.tool.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import com.agentplatform.modules.tool.infrastructure.persistence.entity.McpServerEntity;

/**
 * {@code mcp_servers} 表 MyBatis-Plus Mapper（infrastructure 层）。
 *
 * <p>仅使用 {@link BaseMapper} 提供的通用 CRUD，无自定义 SQL；由持久化仓储
 * 包装使用，不对外暴露。
 */
public interface McpServerMapper extends BaseMapper<McpServerEntity> {
}
