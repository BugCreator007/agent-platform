package com.agentplatform.modules.workflow.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import com.agentplatform.modules.workflow.infrastructure.persistence.entity.WorkflowNodeRunEntity;
import java.util.List;

/**
 * 节点运行记录 MyBatis-Plus Mapper。
 *
 * <p>所属层：infrastructure 基础设施层。除基础 CRUD 外，提供按实例查询并按开始时间升序的
 * 自定义查询，SQL 定义在 {@code resources/mapper/WorkflowNodeRunMapper.xml}。</p>
 */
public interface WorkflowNodeRunMapper extends BaseMapper<WorkflowNodeRunEntity> {
    /**
     * 查询某实例的全部节点运行记录，按开始时间升序。
     *
     * @param instanceId 流程实例 ID
     * @return 节点运行记录列表
     */
    List<WorkflowNodeRunEntity> findByInstanceIdOrderByStartedAtAsc(@Param("instanceId") String instanceId);
}
