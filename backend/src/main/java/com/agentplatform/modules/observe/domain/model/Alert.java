package com.agentplatform.modules.observe.domain.model;

public class Alert {
    private final String id;
    private final String severity;
    private final String title;
    private final String rule;
    private final String target;
    private final String time;
    private final String ago;
    private String status;
    private final String metric;
    private final String detail;

    public Alert(String id, String severity, String title, String rule, String target, String time,
                 String ago, String status, String metric, String detail) {
        this.id = id; this.severity = severity; this.title = title; this.rule = rule;
        this.target = target; this.time = time; this.ago = ago; this.status = status;
        this.metric = metric; this.detail = detail;
    }

    public void ack() { this.status = "ack"; }
    public void resolve() { this.status = "resolved"; }
    public String id() { return id; }
    public String severity() { return severity; }
    public String title() { return title; }
    public String rule() { return rule; }
    public String target() { return target; }
    public String time() { return time; }
    public String ago() { return ago; }
    public String status() { return status; }
    public String metric() { return metric; }
    public String detail() { return detail; }
}
