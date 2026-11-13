package com.agentplatform.modules.tool.domain.model;

public class Tool {
    private final String name;
    private final String type;
    private final String owner;
    private final long calls;
    private final double errorRate;
    private String health;
    private String checked;
    private boolean enabled;
    private final String description;
    private final String endpoint;

    public Tool(String name, String type, String owner, long calls, double errorRate,
                String health, String checked, boolean enabled, String description) {
        this(name, type, owner, calls, errorRate, health, checked, enabled, description, null);
    }

    public Tool(String name, String type, String owner, long calls, double errorRate,
                String health, String checked, boolean enabled, String description, String endpoint) {
        this.name = name; this.type = type; this.owner = owner; this.calls = calls;
        this.errorRate = errorRate; this.health = health; this.checked = checked;
        this.enabled = enabled; this.description = description; this.endpoint = endpoint;
    }

    public void toggle() { this.enabled = !this.enabled; }
    public void check() { this.checked = "刚刚"; this.health = errorRate > 1 ? "degraded" : "healthy"; }
    public String name() { return name; }
    public String type() { return type; }
    public String owner() { return owner; }
    public long calls() { return calls; }
    public double errorRate() { return errorRate; }
    public String health() { return health; }
    public String checked() { return checked; }
    public boolean enabled() { return enabled; }
    public String description() { return description; }
    public String endpoint() { return endpoint; }
}
