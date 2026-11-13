package com.agentplatform.modules.workflow.domain.model;

import java.util.List;

public class Workflow {
    private final String id;
    private final String name;
    private String status;
    private final String trigger;
    private long runs;
    private double success;
    private final String owner;
    private String version;
    private String updated;
    private final List<WorkflowNode> nodes;

    public Workflow(String id, String name, String status, String trigger, long runs, double success,
                    String owner, String version, String updated, List<WorkflowNode> nodes) {
        this.id = id; this.name = name; this.status = status; this.trigger = trigger;
        this.runs = runs; this.success = success; this.owner = owner; this.version = version;
        this.updated = updated; this.nodes = List.copyOf(nodes);
    }

    public void publish() {
        this.status = "published";
        int current = Integer.parseInt(version.replaceAll("\\D", ""));
        this.version = "v" + (current + 1);
        this.updated = "刚刚";
    }
    public void recordRun(boolean ok) {
        this.runs++;
        this.success = Math.round(((this.success * (this.runs - 1)) + (ok ? 100 : 0)) / this.runs * 10.0) / 10.0;
        this.updated = "刚刚";
    }

    public String id() { return id; }
    public String name() { return name; }
    public String status() { return status; }
    public String trigger() { return trigger; }
    public long runs() { return runs; }
    public double success() { return success; }
    public String owner() { return owner; }
    public String version() { return version; }
    public String updated() { return updated; }
    public List<WorkflowNode> nodes() { return nodes; }
}
