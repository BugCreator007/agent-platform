package com.agentplatform.modules.workflow.domain.model;

import java.time.Instant;

public class WorkflowInstance {
    private final String id;
    private final String workflowId;
    private final String workflowName;
    private String status;
    private final String triggerType;
    private final String inputJson;
    private String outputJson;
    private String currentNode;
    private final Instant startedAt;
    private Instant finishedAt;
    private String error;

    public WorkflowInstance(String id, String workflowId, String workflowName, String status,
                            String triggerType, String inputJson, String outputJson, String currentNode,
                            Instant startedAt, Instant finishedAt, String error) {
        this.id=id; this.workflowId=workflowId; this.workflowName=workflowName; this.status=status;
        this.triggerType=triggerType; this.inputJson=inputJson; this.outputJson=outputJson;
        this.currentNode=currentNode; this.startedAt=startedAt; this.finishedAt=finishedAt; this.error=error;
    }
    public void waitApproval(String nodeKey) { this.status="WAITING_APPROVAL"; this.currentNode=nodeKey; }
    public void running(String nodeKey) { this.status="RUNNING"; this.currentNode=nodeKey; }
    public void succeed(String output) { this.status="SUCCEEDED"; this.outputJson=output; this.finishedAt=Instant.now(); }
    public void fail(String error) { this.status="FAILED"; this.error=error; this.finishedAt=Instant.now(); }
    public void cancel() { this.status="CANCELLED"; this.finishedAt=Instant.now(); }
    public String id() { return id; }
    public String workflowId() { return workflowId; }
    public String workflowName() { return workflowName; }
    public String status() { return status; }
    public String triggerType() { return triggerType; }
    public String inputJson() { return inputJson; }
    public String outputJson() { return outputJson; }
    public String currentNode() { return currentNode; }
    public Instant startedAt() { return startedAt; }
    public Instant finishedAt() { return finishedAt; }
    public String error() { return error; }
}
