package com.agentplatform.modules.observe.application.query;

import com.agentplatform.modules.agent.application.query.AgentSummary;
import com.agentplatform.modules.observe.domain.model.Alert;
import com.agentplatform.modules.release.domain.model.Release;

import java.util.List;

public record OverviewResponse(
        Kpi kpi, List<AgentSummary> agents, List<Alert> alerts, List<Release> releases
) {
    public record Kpi(String todaySessions, String successRate, String p95, String cost,
                      String costDelta, String sessionsDelta) {}
}
