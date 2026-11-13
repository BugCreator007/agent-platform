package com.agentplatform.modules.workflow.domain.model;

import java.time.Instant;

public class WorkflowNodeRun {
    private final String id;
    private final String instanceId;
    private final String nodeKey;
    private final String nodeType;
    private final String title;
    private String status;
    private final String inputJson;
    private String outputJson;
    private final int attempt;
    private final Instant startedAt;
    private Instant finishedAt;
    private String error;

    public WorkflowNodeRun(String id, String instanceId, String nodeKey, String nodeType, String title,
                           String status, String inputJson, String outputJson, int attempt,
                           Instant startedAt, Instant finishedAt, String error) {
        this.id=id; this.instanceId=instanceId; this.nodeKey=nodeKey; this.nodeType=nodeType; this.title=title;
        this.status=status; this.inputJson=inputJson; this.outputJson=outputJson; this.attempt=attempt;
        this.startedAt=startedAt; this.finishedAt=finishedAt; this.error=error;
    }
    public void succeed(String output) { this.status="SUCCESS"; this.outputJson=output; this.finishedAt=Instant.now(); }
    public void waitApproval() { this.status="WAITING_APPROVAL"; }
    public void waitTimer() { this.status="WAITING_TIMER"; }
    public void waitSubflow() { this.status="WAITING_SUBFLOW"; }
    public void skip(String reason) { this.status="SKIPPED"; this.outputJson=reason; this.finishedAt=Instant.now(); }
    public void fail(String error) { this.status="FAILED"; this.error=error; this.finishedAt=Instant.now(); }
    public String id() { return id; }
    public String instanceId() { return instanceId; }
    public String nodeKey() { return nodeKey; }
    public String nodeType() { return nodeType; }
    public String title() { return title; }
    public String status() { return status; }
    public String inputJson() { return inputJson; }
    public String outputJson() { return outputJson; }
    public int attempt() { return attempt; }
    public Instant startedAt() { return startedAt; }
    public Instant finishedAt() { return finishedAt; }
    public String error() { return error; }
}
