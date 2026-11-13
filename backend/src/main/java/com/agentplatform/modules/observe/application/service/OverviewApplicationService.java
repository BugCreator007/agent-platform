package com.agentplatform.modules.observe.application.service;

import com.agentplatform.modules.agent.application.service.AgentApplicationService;
import com.agentplatform.modules.observe.application.query.OverviewResponse;
import com.agentplatform.modules.release.application.service.ReleaseApplicationService;
import org.springframework.stereotype.Service;

@Service
public class OverviewApplicationService {
    private final AgentApplicationService agents;
    private final AlertApplicationService alerts;
    private final ReleaseApplicationService releases;

    public OverviewApplicationService(AgentApplicationService agents, AlertApplicationService alerts,
                                      ReleaseApplicationService releases) {
        this.agents = agents;
        this.alerts = alerts;
        this.releases = releases;
    }

    public OverviewResponse overview() {
        return new OverviewResponse(
                new OverviewResponse.Kpi("6,508", "98.6%", "1.24s", "¥12,480", "+8.2%", "+2.8%"),
                agents.list(null, null),
                alerts.list(null),
                releases.list()
        );
    }
}
