package com.agentplatform.modules.release.domain.model;

public class Release {
    private final String id;
    private final String agentId;
    private final String agentName;
    private final String version;
    private String stage;
    private int traffic;
    private final Double evalScore;
    private final String owner;
    private final String time;
    private final String note;

    public Release(String id, String agentId, String agentName, String version, String stage,
                   int traffic, Double evalScore, String owner, String time, String note) {
        this.id = id;
        this.agentId = agentId;
        this.agentName = agentName;
        this.version = version;
        this.stage = stage;
        this.traffic = traffic;
        this.evalScore = evalScore;
        this.owner = owner;
        this.time = time;
        this.note = note;
    }

    public void approve() {
        if (!"approve".equals(stage)) throw new IllegalStateException("当前状态不能审批");
        this.stage = "canary";
        this.traffic = 5;
    }
    public void promote() {
        if ("draft".equals(stage)) { this.stage = "approve"; return; }
        if ("canary".equals(stage)) { this.stage = "live"; this.traffic = 100; return; }
        throw new IllegalStateException("当前状态不能推进");
    }
    public void rollback() { this.stage = "rollback"; this.traffic = 0; }
    public void updateTraffic(int traffic) { this.traffic = Math.max(0, Math.min(100, traffic)); }
    public String id() { return id; }
    public String agentId() { return agentId; }
    public String agentName() { return agentName; }
    public String version() { return version; }
    public String stage() { return stage; }
    public int traffic() { return traffic; }
    public Double evalScore() { return evalScore; }
    public String owner() { return owner; }
    public String time() { return time; }
    public String note() { return note; }
}
