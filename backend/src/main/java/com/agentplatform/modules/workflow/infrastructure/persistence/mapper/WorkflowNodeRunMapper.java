package com.agentplatform.modules.workflow.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import com.agentplatform.modules.workflow.infrastructure.persistence.entity.WorkflowNodeRunEntity;
import java.util.List;

public interface WorkflowNodeRunMapper extends BaseMapper<WorkflowNodeRunEntity> {
    List<WorkflowNodeRunEntity> findByInstanceIdOrderByStartedAtAsc(@Param("instanceId") String instanceId);
}
