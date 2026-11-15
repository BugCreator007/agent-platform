package com.agentplatform.modules.workflow.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import com.agentplatform.modules.workflow.domain.model.WorkflowInstance;

import java.time.Instant;

@TableName(value = "workflow_instances", autoResultMap = true)
public class WorkflowInstanceEntity {
    @TableId(type = IdType.INPUT) private String id;
    @TableField(value = "workflow_id") private String workflowId;
    @TableField(value = "workflow_name") private String workflowName;
    private String status;
    @TableField(value = "trigger_type") private String triggerType;
    @TableField(value = "input_json") private String inputJson;
    @TableField(value = "output_json") private String outputJson;
    @TableField(value = "current_node") private String currentNode;
    @TableField(value = "started_at") private Instant startedAt;
    @TableField(value = "finished_at") private Instant finishedAt;
    private String error;
    @TableField(value = "state_json") private String stateJson;
    @TableField(value = "resume_at") private Instant resumeAt;
    public static WorkflowInstanceEntity from(WorkflowInstance i) {
        WorkflowInstanceEntity e = new WorkflowInstanceEntity();
        e.id=i.id(); e.workflowId=i.workflowId(); e.workflowName=i.workflowName(); e.status=i.status();
        e.triggerType=i.triggerType(); e.inputJson=i.inputJson(); e.outputJson=i.outputJson();
        e.currentNode=i.currentNode(); e.startedAt=i.startedAt(); e.finishedAt=i.finishedAt(); e.error=i.error();
        e.stateJson=i.stateJson(); e.resumeAt=i.resumeAt(); return e;
    }
    public WorkflowInstance toDomain() { return new WorkflowInstance(id, workflowId, workflowName, status,
            triggerType, inputJson, outputJson, currentNode, startedAt, finishedAt, error, stateJson, resumeAt); }
}
