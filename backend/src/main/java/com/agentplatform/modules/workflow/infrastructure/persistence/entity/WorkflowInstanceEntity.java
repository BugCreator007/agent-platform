package com.agentplatform.modules.workflow.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import com.agentplatform.modules.workflow.domain.model.WorkflowInstance;

import java.time.Instant;

/**
 * 流程实例持久化实体，对应表 {@code workflow_instances}。
 *
 * <p>所属层：infrastructure 基础设施层。完整映射实例状态机所需的字段，其中
 * {@code state_json} 保存执行游标、{@code resume_at} 保存定时器到期时间，
 * 两者是等待态恢复的关键；与领域模型 {@link WorkflowInstance} 通过静态方法互转。</p>
 *
 * <p>约束：主键由应用生成（{@code IdType.INPUT}）；时间字段使用 {@link Instant}，
 * 依赖 MyBatis-Plus/JDBC 驱动映射到数据库时间列。</p>
 */
@TableName(value = "workflow_instances", autoResultMap = true)
public class WorkflowInstanceEntity {
    /** 实例 ID（主键，应用生成）。 */
    @TableId(type = IdType.INPUT) private String id;
    /** 流程定义 ID。 */
    @TableField(value = "workflow_id") private String workflowId;
    /** 流程名称快照。 */
    @TableField(value = "workflow_name") private String workflowName;
    /** 实例状态。 */
    private String status;
    /** 触发类型（manual/scheduled）。 */
    @TableField(value = "trigger_type") private String triggerType;
    /** 启动输入 JSON。 */
    @TableField(value = "input_json") private String inputJson;
    /** 输出 JSON。 */
    @TableField(value = "output_json") private String outputJson;
    /** 当前节点 key。 */
    @TableField(value = "current_node") private String currentNode;
    /** 启动时间。 */
    @TableField(value = "started_at") private Instant startedAt;
    /** 结束时间。 */
    @TableField(value = "finished_at") private Instant finishedAt;
    /** 失败原因。 */
    private String error;
    /** 执行游标 JSON。 */
    @TableField(value = "state_json") private String stateJson;
    /** 定时器恢复时间。 */
    @TableField(value = "resume_at") private Instant resumeAt;

    /**
     * 领域模型 → 持久化实体。
     *
     * @param i 流程实例
     * @return 持久化实体
     */
    public static WorkflowInstanceEntity from(WorkflowInstance i) {
        WorkflowInstanceEntity e = new WorkflowInstanceEntity();
        e.id=i.id(); e.workflowId=i.workflowId(); e.workflowName=i.workflowName(); e.status=i.status();
        e.triggerType=i.triggerType(); e.inputJson=i.inputJson(); e.outputJson=i.outputJson();
        e.currentNode=i.currentNode(); e.startedAt=i.startedAt(); e.finishedAt=i.finishedAt(); e.error=i.error();
        e.stateJson=i.stateJson(); e.resumeAt=i.resumeAt(); return e;
    }

    /**
     * 持久化实体 → 领域模型。
     *
     * @return 流程实例
     */
    public WorkflowInstance toDomain() { return new WorkflowInstance(id, workflowId, workflowName, status,
            triggerType, inputJson, outputJson, currentNode, startedAt, finishedAt, error, stateJson, resumeAt); }
}
