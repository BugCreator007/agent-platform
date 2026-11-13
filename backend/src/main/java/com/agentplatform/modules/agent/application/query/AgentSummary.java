package com.agentplatform.modules.agent.application.query;

import com.agentplatform.modules.agent.domain.model.Agent;

import java.util.List;

public record AgentSummary(
        String id, String name, String code, String status, String version,
        String model, String providerId, String modelId, String owner, String team, String release,
        long sessions, double success, String p95, double cost, String updated,
        String prompt, List<String> tools, List<String> kb
) {
    public static AgentSummary from(Agent agent) {
        return new AgentSummary(
                agent.id(), agent.name(), agent.code(), agent.status().name().toLowerCase(),
                agent.version(), agent.model(), agent.providerId(), agent.modelId(),
                agent.owner(), agent.team(), agent.release(),
                agent.sessions(), agent.success(), agent.p95(), agent.cost(), agent.updated(),
                agent.prompt(), agent.tools(), agent.kb()
        );
    }
}
