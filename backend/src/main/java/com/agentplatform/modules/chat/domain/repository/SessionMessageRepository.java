package com.agentplatform.modules.chat.domain.repository;

import com.agentplatform.modules.chat.domain.model.SessionMessage;

import java.util.List;

public interface SessionMessageRepository {
    void save(SessionMessage message);
    List<SessionMessage> findBySession(String sessionId);
    void deleteBySession(String sessionId);
}
