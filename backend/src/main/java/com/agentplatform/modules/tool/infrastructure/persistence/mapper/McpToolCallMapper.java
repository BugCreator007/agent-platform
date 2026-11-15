package com.agentplatform.modules.tool.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import com.agentplatform.modules.tool.infrastructure.persistence.entity.McpToolCallEntity;
import java.util.List;

public interface McpToolCallMapper extends BaseMapper<McpToolCallEntity> {
    List<McpToolCallEntity> findTop50ByServerIdOrderByCreatedAtDesc(@Param("serverId") String serverId);
}
