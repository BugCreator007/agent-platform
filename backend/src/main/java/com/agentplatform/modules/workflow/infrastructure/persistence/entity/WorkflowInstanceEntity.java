package com.agentplatform.modules.workflow.infrastructure.persistence.entity;

import com.agentplatform.modules.workflow.domain.model.WorkflowInstance;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "workflow_instances")
public class WorkflowInstanceEntity {
    @Id private String id;
    @Column(name = "workflow_id") private String workflowId;
    @Column(name = "workflow_name") private String workflowName;
    private String status;
    @Column(name = "trigger_type") private String triggerType;
    @Column(name = "input_json", columnDefinition = "TEXT") private String inputJson;
    @Column(name = "output_json", columnDefinition = "TEXT") private String outputJson;
    @Column(name = "current_node") private String currentNode;
    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "finished_at") private Instant finishedAt;
    @Column(columnDefinition = "TEXT") private String error;
    public static WorkflowInstanceEntity from(WorkflowInstance i) {
        WorkflowInstanceEntity e = new WorkflowInstanceEntity();
        e.id=i.id(); e.workflowId=i.workflowId(); e.workflowName=i.workflowName(); e.status=i.status();
        e.triggerType=i.triggerType(); e.inputJson=i.inputJson(); e.outputJson=i.outputJson();
        e.currentNode=i.currentNode(); e.startedAt=i.startedAt(); e.finishedAt=i.finishedAt(); e.error=i.error(); return e;
    }
    public WorkflowInstance toDomain() { return new WorkflowInstance(id, workflowId, workflowName, status,
            triggerType, inputJson, outputJson, currentNode, startedAt, finishedAt, error); }
}
