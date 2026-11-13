package com.agentplatform.modules.workflow.infrastructure.persistence.entity;

import com.agentplatform.modules.workflow.domain.model.WorkflowNodeRun;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "workflow_node_runs")
public class WorkflowNodeRunEntity {
    @Id private String id;
    @Column(name = "instance_id") private String instanceId;
    @Column(name = "node_key") private String nodeKey;
    @Column(name = "node_type") private String nodeType;
    private String title;
    private String status;
    @Column(name = "input_json", columnDefinition = "TEXT") private String inputJson;
    @Column(name = "output_json", columnDefinition = "TEXT") private String outputJson;
    private int attempt;
    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "finished_at") private Instant finishedAt;
    @Column(columnDefinition = "TEXT") private String error;
    public static WorkflowNodeRunEntity from(WorkflowNodeRun r) {
        WorkflowNodeRunEntity e = new WorkflowNodeRunEntity();
        e.id=r.id(); e.instanceId=r.instanceId(); e.nodeKey=r.nodeKey(); e.nodeType=r.nodeType(); e.title=r.title();
        e.status=r.status(); e.inputJson=r.inputJson(); e.outputJson=r.outputJson(); e.attempt=r.attempt();
        e.startedAt=r.startedAt(); e.finishedAt=r.finishedAt(); e.error=r.error(); return e;
    }
    public WorkflowNodeRun toDomain() { return new WorkflowNodeRun(id, instanceId, nodeKey, nodeType, title,
            status, inputJson, outputJson, attempt, startedAt, finishedAt, error); }
}
