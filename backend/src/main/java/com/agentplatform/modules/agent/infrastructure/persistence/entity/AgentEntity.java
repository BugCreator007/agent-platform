package com.agentplatform.modules.agent.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.agentplatform.common.persistence.StringListTypeHandler;

import com.agentplatform.modules.agent.domain.model.Agent;
import com.agentplatform.modules.agent.domain.model.AgentStatus;

import java.util.List;

@TableName(value = "agents", autoResultMap = true)
public class AgentEntity {
    @TableId(type = IdType.INPUT) private String id;
    private String code;
    private String name;
    private String status;
    private String version;
    private String model;
    @TableField(value = "provider_id") private String providerId;
    @TableField(value = "model_id") private String modelId;
    private String owner;
    private String team;
    @TableField(value = "release_status") private String releaseStatus;
    private String prompt;
    @TableField(typeHandler = StringListTypeHandler.class) private List<String> tools;
    @TableField(typeHandler = StringListTypeHandler.class) private List<String> kb;
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
