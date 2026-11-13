package com.agentplatform.modules.tool.infrastructure.persistence.entity;

import com.agentplatform.modules.tool.domain.model.Tool;
import jakarta.persistence.*;

@Entity
@Table(name = "tools")
public class ToolEntity {
    @Id private String name;
    private String type;
    private String owner;
    private long calls;
    @Column(name = "error_rate") private double errorRate;
    private String health;
    private String checked;
    private boolean enabled;
    @Column(columnDefinition = "TEXT") private String description;
    private String endpoint;
    public static ToolEntity from(Tool t) {
        ToolEntity e = new ToolEntity(); e.name=t.name(); e.type=t.type(); e.owner=t.owner(); e.calls=t.calls();
        e.errorRate=t.errorRate(); e.health=t.health(); e.checked=t.checked(); e.enabled=t.enabled(); e.description=t.description(); e.endpoint=t.endpoint(); return e;
    }
    public Tool toDomain() { return new Tool(name, type, owner, calls, errorRate, health, checked, enabled, description, endpoint); }
    public String getName() { return name; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
}
