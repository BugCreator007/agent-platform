package com.agentplatform.modules.agent.infrastructure.persistence.entity;

import com.agentplatform.common.persistence.StringListConverter;
import com.agentplatform.modules.agent.domain.model.Agent;
import com.agentplatform.modules.agent.domain.model.AgentStatus;
import jakarta.persistence.*;

import java.util.List;

@Entity
@Table(name = "agents")
public class AgentEntity {
    @Id private String id;
    @Column(nullable = false, unique = true) private String code;
    @Column(nullable = false) private String name;
    @Column(nullable = false) private String status;
    @Column(nullable = false) private String version;
    private String model;
    @Column(name = "provider_id") private String providerId;
    @Column(name = "model_id") private String modelId;
    private String owner;
    private String team;
    @Column(name = "release_status") private String releaseStatus;
    @Column(columnDefinition = "TEXT") private String prompt;
    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "TEXT") private List<String> tools;
    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "TEXT") private List<String> kb;
    private long sessions;
    private double success;
    private String p95;
    private double cost;
    private String updated;

    public static AgentEntity from(Agent a) {
        AgentEntity e = new AgentEntity();
        e.id = a.id(); e.code = a.code(); e.name = a.name(); e.status = a.status().name().toLowerCase();
        e.version = a.version(); e.model = a.model(); e.providerId = a.providerId(); e.modelId = a.modelId();
        e.owner = a.owner(); e.team = a.team();
        e.releaseStatus = a.release(); e.prompt = a.prompt(); e.tools = a.tools(); e.kb = a.kb();
        e.sessions = a.sessions(); e.success = a.success(); e.p95 = a.p95(); e.cost = a.cost(); e.updated = a.updated();
        return e;
    }

    public Agent toDomain() {
        return Agent.restore(id, code, name, AgentStatus.valueOf(status.toUpperCase()), version, model,
                providerId, modelId, owner, team, releaseStatus, prompt, tools == null ? List.of() : tools,
                kb == null ? List.of() : kb, sessions, success, p95, cost, updated);
    }

    public String getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
}
