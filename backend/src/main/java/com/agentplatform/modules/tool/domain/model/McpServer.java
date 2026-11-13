package com.agentplatform.modules.tool.domain.model;

import java.time.Instant;

public class McpServer {
    private final String id;
    private String name;
    private String transport;
    private String url;
    private String command;
    private String args;
    private String authType;
    private String authToken;
    private boolean enabled;
    private String status;
    private String lastError;
    private Instant lastConnectedAt;

    public McpServer(String id, String name, String transport, String url, String command, String args,
                     String authType, String authToken, boolean enabled, String status,
                     String lastError, Instant lastConnectedAt) {
        this.id=id; this.name=name; this.transport=transport; this.url=url; this.command=command;
        this.args=args; this.authType=authType; this.authToken=authToken; this.enabled=enabled;
        this.status=status; this.lastError=lastError; this.lastConnectedAt=lastConnectedAt;
    }

    public void update(String name, String transport, String url, String command, String args,
                       String authType, String authToken, Boolean enabled) {
        if (name != null && !name.isBlank()) this.name = name;
        if (transport != null) this.transport = transport;
        if (url != null) this.url = url;
        if (command != null) this.command = command;
        if (args != null) this.args = args;
        if (authType != null) this.authType = authType;
        if (authToken != null) this.authToken = authToken;
        if (enabled != null) this.enabled = enabled;
    }

    public void connected() { this.status="CONNECTED"; this.lastError=null; this.lastConnectedAt=Instant.now(); }
    public void failed(String error) { this.status="ERROR"; this.lastError=error; }
    public void disconnected() { this.status="DISCONNECTED"; }

    public String id() { return id; }
    public String name() { return name; }
    public String transport() { return transport; }
    public String url() { return url; }
    public String command() { return command; }
    public String args() { return args; }
    public String authType() { return authType; }
    public String authToken() { return authToken; }
    public boolean enabled() { return enabled; }
    public String status() { return status; }
    public String lastError() { return lastError; }
    public Instant lastConnectedAt() { return lastConnectedAt; }
}
