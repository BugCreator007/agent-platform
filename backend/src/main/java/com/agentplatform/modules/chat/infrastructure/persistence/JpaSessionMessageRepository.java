package com.agentplatform.modules.chat.infrastructure.persistence;

import com.agentplatform.modules.chat.domain.model.SessionMessage;
import com.agentplatform.modules.chat.domain.repository.SessionMessageRepository;
import com.agentplatform.modules.chat.infrastructure.persistence.entity.SessionMessageEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public class JpaSessionMessageRepository implements SessionMessageRepository {
    private final SessionMessageJpaRepository jpa;
    public JpaSessionMessageRepository(SessionMessageJpaRepository jpa) { this.jpa = jpa; }
    @Override public void save(SessionMessage message) { jpa.save(SessionMessageEntity.from(message)); }
    @Override public List<SessionMessage> findBySession(String sessionId) {
        return jpa.findBySessionIdOrderByCreatedAtAsc(sessionId).stream().map(SessionMessageEntity::toDomain).toList();
    }
    @Override @Transactional public void deleteBySession(String sessionId) { jpa.deleteBySessionId(sessionId); }
}
