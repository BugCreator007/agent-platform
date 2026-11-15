package com.agentplatform.modules.tool.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import com.agentplatform.modules.tool.infrastructure.persistence.entity.ToolEntity;

/**
 * {@code tools} 表 MyBatis-Plus Mapper（infrastructure 层）。
 *
 * <p>仅使用 {@link BaseMapper} 提供的通用 CRUD；主键为工具名称，由持久化仓储
 * 调用 {@code selectById}/{@code deleteById} 实现按名称读写。
 */
public interface ToolMapper extends BaseMapper<ToolEntity> {
}
