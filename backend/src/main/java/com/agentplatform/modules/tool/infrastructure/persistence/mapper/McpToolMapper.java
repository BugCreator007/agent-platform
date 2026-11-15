package com.agentplatform.modules.tool.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import com.agentplatform.modules.tool.infrastructure.persistence.entity.McpToolEntity;
import java.util.List;

public interface McpToolMapper extends BaseMapper<McpToolEntity> {
    List<McpToolEntity> findByServerId(@Param("serverId") String serverId);

    List<McpToolEntity> findByToolKeyIn(@Param("keys") List<String> keys);

    McpToolEntity findByToolKey(@Param("toolKey") String toolKey);

    int deleteByServerId(@Param("serverId") String serverId);
}
