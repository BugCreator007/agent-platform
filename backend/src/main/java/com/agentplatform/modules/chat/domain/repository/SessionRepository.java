package com.agentplatform.modules.chat.domain.repository;

import com.agentplatform.modules.chat.domain.model.ChatSession;

import java.util.List;
import java.util.Optional;

public interface SessionRepository {
    List<ChatSession> findAll();
    Optional<ChatSession> findById(String id);
    void save(ChatSession session);
    void deleteById(String id);
}
