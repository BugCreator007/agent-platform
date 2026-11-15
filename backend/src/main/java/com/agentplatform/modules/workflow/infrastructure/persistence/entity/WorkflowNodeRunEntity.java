package com.agentplatform.modules.workflow.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import com.agentplatform.modules.workflow.domain.model.WorkflowNodeRun;

import java.time.Instant;

@TableName(value = "workflow_node_runs", autoResultMap = true)
public class WorkflowNodeRunEntity {
    @TableId(type = IdType.INPUT) private String id;
    @TableField(value = "instance_id") private String instanceId;
    @TableField(value = "node_key") private String nodeKey;
    @TableField(value = "node_type") private String nodeType;
    private String title;
    private String status;
    @TableField(value = "input_json") private String inputJson;
    @TableField(value = "output_json") private String outputJson;
    private int attempt;
    @TableField(value = "started_at") private Instant startedAt;
    @TableField(value = "finished_at") private Instant finishedAt;
    private String error;
    public static WorkflowNodeRunEntity from(WorkflowNodeRun r) {
        WorkflowNodeRunEntity e = new WorkflowNodeRunEntity();
        e.id=r.id(); e.instanceId=r.instanceId(); e.nodeKey=r.nodeKey(); e.nodeType=r.nodeType(); e.title=r.title();
        e.status=r.status(); e.inputJson=r.inputJson(); e.outputJson=r.outputJson(); e.attempt=r.attempt();
        e.startedAt=r.startedAt(); e.finishedAt=r.finishedAt(); e.error=r.error(); return e;
    }
    public WorkflowNodeRun toDomain() { return new WorkflowNodeRun(id, instanceId, nodeKey, nodeType, title,
            status, inputJson, outputJson, attempt, startedAt, finishedAt, error); }
}
