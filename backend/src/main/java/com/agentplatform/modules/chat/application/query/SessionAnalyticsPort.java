package com.agentplatform.modules.chat.application.query;

import java.time.Instant;
import java.util.List;

public interface SessionAnalyticsPort {
    List<SessionFact> sessionsBetween(Instant from, Instant to);
}
