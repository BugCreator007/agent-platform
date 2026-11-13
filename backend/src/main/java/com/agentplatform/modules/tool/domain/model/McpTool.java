package com.agentplatform.modules.tool.domain.model;

public class McpTool {
    private final String id;
    private final String serverId;
    private final String toolKey;
    private final String name;
    private final String description;
    private final String inputSchema;
    private boolean enabled;
    private final String riskLevel;

    public McpTool(String id, String serverId, String toolKey, String name, String description,
                   String inputSchema, boolean enabled, String riskLevel) {
        this.id=id; this.serverId=serverId; this.toolKey=toolKey; this.name=name;
        this.description=description; this.inputSchema=inputSchema; this.enabled=enabled;
        this.riskLevel=riskLevel;
    }
    public void toggle() { this.enabled = !enabled; }
    public String id() { return id; }
    public String serverId() { return serverId; }
    public String toolKey() { return toolKey; }
    public String name() { return name; }
    public String description() { return description; }
    public String inputSchema() { return inputSchema; }
    public boolean enabled() { return enabled; }
    public String riskLevel() { return riskLevel; }
}
