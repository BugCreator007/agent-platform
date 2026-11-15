package com.agentplatform.modules.workflow.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import com.agentplatform.modules.workflow.infrastructure.persistence.entity.WorkflowEntity;

/**
 * 流程定义 MyBatis-Plus Mapper。
 *
 * <p>所属层：infrastructure 基础设施层。继承 {@link BaseMapper} 提供
 * select/insert/update/delete 等基础能力，无自定义 SQL。</p>
 */
public interface WorkflowMapper extends BaseMapper<WorkflowEntity> {
}
