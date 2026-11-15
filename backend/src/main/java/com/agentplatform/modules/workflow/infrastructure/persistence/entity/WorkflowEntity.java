package com.agentplatform.modules.workflow.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.agentplatform.modules.workflow.infrastructure.persistence.WorkflowNodeListTypeHandler;

import com.agentplatform.modules.workflow.domain.model.Workflow;
import com.agentplatform.modules.workflow.domain.model.WorkflowNode;

import java.util.List;

@TableName(value = "workflows", autoResultMap = true)
public class WorkflowEntity {
    @TableId(type = IdType.INPUT) private String id;
    private String name;
    private String status;
    @TableField(value = "trigger_label") private String triggerLabel;
    private long runs;
    private double success;
    private String owner;
    private String version;
    private String updated;
    @TableField(value = "nodes_json", typeHandler = WorkflowNodeListTypeHandler.class) private List<WorkflowNode> nodes;

    public static WorkflowEntity from(Workflow w) {
        WorkflowEntity e = new WorkflowEntity();
        e.id=w.id(); e.name=w.name(); e.status=w.status(); e.triggerLabel=w.trigger(); e.runs=w.runs();
        e.success=w.success(); e.owner=w.owner(); e.version=w.version(); e.updated=w.updated(); e.nodes=w.nodes(); return e;
    }
    public Workflow toDomain() {
        return new Workflow(id, name, status, triggerLabel, runs, success, owner, version, updated,
                nodes == null ? List.of() : nodes);
    }
}
