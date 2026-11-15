package com.agentplatform.modules.chat.infrastructure.persistence.repository;

import com.agentplatform.modules.chat.application.query.SessionAnalyticsPort;
import com.agentplatform.modules.chat.application.query.SessionFact;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public class MybatisSessionAnalyticsAdapter implements SessionAnalyticsPort {
    private final ChatSessionRepository sessions;

    public MybatisSessionAnalyticsAdapter(ChatSessionRepository sessions) {
        this.sessions = sessions;
    }

    @Override
    public List<SessionFact> sessionsBetween(Instant from, Instant to) {
        return sessions.findByCreatedAtBetween(from, to).stream()
                .map(e -> new SessionFact(e.getAgentId(), e.getAgentName(), e.getStatus(),
                        e.getTools(), e.getTokens(), e.getMs(), e.getCreatedAt()))
                .toList();
    }
}
