package com.agentplatform.modules.model.domain.model;

public class Provider {
    private final String id;
    private final String name;
    private String status;
    private final String endpoint;
    private long models;
    private long calls;
    private double share;
    private String latency;
    private double success;
    private double quota;
    private double used;
    private String key;
    private String region;
    private String route;

    public Provider(String id, String name, String status, String endpoint, long models, long calls,
                    double share, String latency, double success, double quota, double used,
                    String key, String region, String route) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.endpoint = endpoint;
        this.models = models;
        this.calls = calls;
        this.share = share;
        this.latency = latency;
        this.success = success;
        this.quota = quota;
        this.used = used;
        this.key = key;
        this.region = region;
        this.route = route;
    }

    public void markTested(String latency) {
        this.latency = latency;
        if ("degraded".equals(status)) this.status = "healthy";
    }

    public void rotateKey(String newKey) { this.key = newKey; }
    public void updateQuota(double quota) { this.quota = Math.max(0, quota); }
    public void toggle() {
        this.status = "paused".equals(status) ? "healthy" : "paused";
        this.route = "paused".equals(status) ? "已停用" : "备用";
    }

    public String id() { return id; }
    public String name() { return name; }
    public String status() { return status; }
    public String endpoint() { return endpoint; }
    public long models() { return models; }
    public long calls() { return calls; }
    public double share() { return share; }
    public String latency() { return latency; }
    public double success() { return success; }
    public double quota() { return quota; }
    public double used() { return used; }
    public String key() { return key; }
    public String region() { return region; }
    public String route() { return route; }
}
