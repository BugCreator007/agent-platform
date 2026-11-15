package com.agentplatform.modules.tool.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import com.agentplatform.modules.tool.infrastructure.persistence.entity.McpToolCallEntity;
import java.util.List;

/**
 * {@code mcp_tool_calls} 表 MyBatis-Plus Mapper（infrastructure 层）。
 *
 * <p>除通用 CRUD 外，提供按服务查询最近调用记录的 SQL（定义在
 * {@code resources/mapper/McpToolCallMapper.xml}）。
 */
public interface McpToolCallMapper extends BaseMapper<McpToolCallEntity> {
    /**
     * 查询某服务最近 50 条调用记录，按创建时间倒序。
     *
     * <p>固定取 50 条，调用方再按需截断，避免一次拉取过多审计数据。
     *
     * @param serverId 服务主键
     * @return 调用记录实体列表
     */
    List<McpToolCallEntity> findTop50ByServerIdOrderByCreatedAtDesc(@Param("serverId") String serverId);
}
