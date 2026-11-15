package com.agentplatform.modules.tool.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import com.agentplatform.modules.tool.infrastructure.persistence.entity.McpToolEntity;
import java.util.List;

/**
 * {@code mcp_tools} 表 MyBatis-Plus Mapper（infrastructure 层）。
 *
 * <p>提供按服务、工具键查询及按服务批量删除的 SQL（定义在
 * {@code resources/mapper/McpToolMapper.xml}），由持久化仓储包装使用。
 */
public interface McpToolMapper extends BaseMapper<McpToolEntity> {
    /**
     * 查询某服务下的全部工具。
     *
     * @param serverId 服务主键
     * @return 工具实体列表
     */
    List<McpToolEntity> findByServerId(@Param("serverId") String serverId);

    /**
     * 按平台工具键批量查询工具。
     *
     * @param keys 工具键列表（调用方需保证非空）
     * @return 命中的工具实体列表
     */
    List<McpToolEntity> findByToolKeyIn(@Param("keys") List<String> keys);

    /**
     * 按平台工具键查询单个工具。
     *
     * @param toolKey 工具键
     * @return 工具实体；不存在时返回 {@code null}
     */
    McpToolEntity findByToolKey(@Param("toolKey") String toolKey);

    /**
     * 删除某服务下的全部工具。
     *
     * @param serverId 服务主键
     * @return 删除行数
     */
    int deleteByServerId(@Param("serverId") String serverId);
}
