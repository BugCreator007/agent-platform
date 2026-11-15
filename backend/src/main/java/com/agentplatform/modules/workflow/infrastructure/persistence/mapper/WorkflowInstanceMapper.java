package com.agentplatform.modules.workflow.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import com.agentplatform.modules.workflow.infrastructure.persistence.entity.WorkflowInstanceEntity;

/**
 * 流程实例 MyBatis-Plus Mapper。
 *
 * <p>所属层：infrastructure 基础设施层。继承 {@link BaseMapper} 提供基础 CRUD，
 * 无自定义 SQL；实例查询统一走主键或全表扫描（由调度器使用）。</p>
 */
public interface WorkflowInstanceMapper extends BaseMapper<WorkflowInstanceEntity> {
}
