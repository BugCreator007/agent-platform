package com.agentplatform.modules.chat.infrastructure.persistence;

import com.agentplatform.modules.chat.domain.model.ChatSession;
import com.agentplatform.modules.chat.domain.repository.SessionRepository;
import com.agentplatform.modules.chat.infrastructure.persistence.entity.ChatSessionEntity;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Primary
@Repository
public class JpaSessionRepository implements SessionRepository {
    private final SessionJpaRepository jpa;
    public JpaSessionRepository(SessionJpaRepository jpa) { this.jpa = jpa; }
    @Override public List<ChatSession> findAll() { return jpa.findAll().stream().map(ChatSessionEntity::toDomain).toList(); }
    @Override public Optional<ChatSession> findById(String id) { return jpa.findById(id).map(ChatSessionEntity::toDomain); }
    @Override public void save(ChatSession session) { jpa.save(ChatSessionEntity.from(session)); }
    @Override public void deleteById(String id) { jpa.deleteById(id); }
}
