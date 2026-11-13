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
    private String stateJson;
    private Instant resumeAt;

    public WorkflowInstance(String id, String workflowId, String workflowName, String status,
                            String triggerType, String inputJson, String outputJson, String currentNode,
                            Instant startedAt, Instant finishedAt, String error) {
        this(id, workflowId, workflowName, status, triggerType, inputJson, outputJson, currentNode,
                startedAt, finishedAt, error, null, null);
    }

    public WorkflowInstance(String id, String workflowId, String workflowName, String status,
                            String triggerType, String inputJson, String outputJson, String currentNode,
                            Instant startedAt, Instant finishedAt, String error,
                            String stateJson, Instant resumeAt) {
        this.id=id; this.workflowId=workflowId; this.workflowName=workflowName; this.status=status;
        this.triggerType=triggerType; this.inputJson=inputJson; this.outputJson=outputJson;
        this.currentNode=currentNode; this.startedAt=startedAt; this.finishedAt=finishedAt; this.error=error;
        this.stateJson=stateJson; this.resumeAt=resumeAt;
    }
    public void waitApproval(String nodeKey) { this.status="WAITING_APPROVAL"; this.currentNode=nodeKey; this.resumeAt=null; }
    public void waitTimer(String nodeKey, Instant resumeAt) { this.status="WAITING_TIMER"; this.currentNode=nodeKey; this.resumeAt=resumeAt; }
    public void waitSubflow(String nodeKey) { this.status="WAITING_SUBFLOW"; this.currentNode=nodeKey; this.resumeAt=null; }
    public void running(String nodeKey) { this.status="RUNNING"; this.currentNode=nodeKey; this.resumeAt=null; }
    public void saveState(String stateJson) { this.stateJson=stateJson; }
    public void succeed(String output) { this.status="SUCCEEDED"; this.outputJson=output; this.finishedAt=Instant.now(); this.resumeAt=null; }
    public void fail(String error) { this.status="FAILED"; this.error=error; this.finishedAt=Instant.now(); this.resumeAt=null; }
    public void cancel() { this.status="CANCELLED"; this.finishedAt=Instant.now(); this.resumeAt=null; }
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
    public String stateJson() { return stateJson; }
    public Instant resumeAt() { return resumeAt; }
}
