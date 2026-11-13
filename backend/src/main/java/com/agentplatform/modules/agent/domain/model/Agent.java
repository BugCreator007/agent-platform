package com.agentplatform.modules.agent.domain.model;

import java.util.List;

public class Agent {
    private final String id;
    private final String code;
    private String name;
    private AgentStatus status;
    private String version;
    private String model;
    private String providerId;
    private String modelId;
    private String owner;
    private String team;
    private String release;
    private String prompt;
    private List<String> tools;
    private List<String> kb;
    private long sessions;
    private double success;
    private String p95;
    private double cost;
    private String updated;

    private Agent(String id, String code, String name, AgentStatus status, String version,
                  String model, String providerId, String modelId, String owner, String team,
                  String release, String prompt, List<String> tools, List<String> kb,
                  long sessions, double success, String p95, double cost, String updated) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.status = status;
        this.version = version;
        this.model = model;
        this.providerId = providerId;
        this.modelId = modelId;
        this.owner = owner;
        this.team = team;
        this.release = release;
        this.prompt = prompt;
        this.tools = List.copyOf(tools);
        this.kb = List.copyOf(kb);
        this.sessions = sessions;
        this.success = success;
        this.p95 = p95;
        this.cost = cost;
        this.updated = updated;
    }

    public static Agent restore(String id, String code, String name, AgentStatus status,
                                String version, String model, String owner, String team,
                                String release, String prompt, List<String> tools, List<String> kb,
                                long sessions, double success, String p95, double cost, String updated) {
        return restore(id, code, name, status, version, model, null, null, owner, team, release,
                prompt, tools, kb, sessions, success, p95, cost, updated);
    }

    public static Agent restore(String id, String code, String name, AgentStatus status,
                                String version, String model, String providerId, String modelId,
                                String owner, String team, String release, String prompt,
                                List<String> tools, List<String> kb, long sessions, double success,
                                String p95, double cost, String updated) {
        return new Agent(id, code, name, status, version, model, providerId, modelId, owner, team,
                release, prompt, tools, kb, sessions, success, p95, cost, updated);
    }

    public static Agent draft(String id, String code, String name) {
        return new Agent(id, code, name, AgentStatus.DRAFT, "v1", "未绑定模型",
                null, null, "顾先路", "客服平台", "草稿", "你是企业的智能助手。",
                List.of(), List.of(), 0, 0, "—", 0, "刚刚");
    }

    public void configure(String model, String owner, String team, String prompt) {
        this.model = model;
        this.owner = owner;
        this.team = team;
        this.prompt = prompt;
        this.updated = "刚刚";
    }

    public void bindModel(String providerId, String modelId, String modelName) {
        this.providerId = providerId;
        this.modelId = modelId;
        if (modelName != null && !modelName.isBlank()) this.model = modelName;
        this.updated = "刚刚";
    }

    public void update(String name, String model, String owner, String team,
                       String prompt, List<String> tools, List<String> kb) {
        update(name, model, null, null, owner, team, prompt, tools, kb);
    }

    public void update(String name, String model, String providerId, String modelId,
                       String owner, String team, String prompt, List<String> tools, List<String> kb) {
        if (name != null && !name.isBlank()) this.name = name;
        if (model != null && !model.isBlank()) this.model = model;
        if (providerId != null) this.providerId = providerId;
        if (modelId != null) this.modelId = modelId;
        if (owner != null && !owner.isBlank()) this.owner = owner;
        if (team != null && !team.isBlank()) this.team = team;
        if (prompt != null) this.prompt = prompt;
        if (tools != null) this.tools = List.copyOf(tools);
        if (kb != null) this.kb = List.copyOf(kb);
        this.updated = "刚刚";
    }

    public void publish() {
        this.status = AgentStatus.RUNNING;
        this.release = "全量";
        this.updated = "刚刚";
    }

    public void pause() {
        this.status = AgentStatus.PAUSED;
        this.release = "已暂停";
        this.updated = "刚刚";
    }

    public void resume() {
        this.status = AgentStatus.RUNNING;
        this.release = "全量";
        this.updated = "刚刚";
    }

    public String id() { return id; }
    public String code() { return code; }
    public String name() { return name; }
    public AgentStatus status() { return status; }
    public String version() { return version; }
    public String model() { return model; }
    public String providerId() { return providerId; }
    public String modelId() { return modelId; }
    public String owner() { return owner; }
    public String team() { return team; }
    public String release() { return release; }
    public String prompt() { return prompt; }
    public List<String> tools() { return tools; }
    public List<String> kb() { return kb; }
    public long sessions() { return sessions; }
    public double success() { return success; }
    public String p95() { return p95; }
    public double cost() { return cost; }
    public String updated() { return updated; }
}
