package com.agentplatform.modules.chat.infrastructure.persistence.repository;

import com.agentplatform.modules.chat.infrastructure.persistence.mapper.SessionMessageMapper;

import com.agentplatform.modules.chat.domain.model.SessionMessage;
import com.agentplatform.modules.chat.infrastructure.persistence.entity.SessionMessageEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public class SessionMessageRepository implements com.agentplatform.modules.chat.domain.repository.SessionMessageRepository {
    private final SessionMessageMapper mapper;

    public SessionMessageRepository(SessionMessageMapper mapper) { this.mapper = mapper; }

    @Override public void save(SessionMessage message) { mapper.insertOrUpdate(SessionMessageEntity.from(message)); }
    @Override public List<SessionMessage> findBySession(String sessionId) {
        return mapper.findBySessionIdOrderByCreatedAtAsc(sessionId).stream().map(SessionMessageEntity::toDomain).toList();
    }
    @Override @Transactional public void deleteBySession(String sessionId) { mapper.deleteBySessionId(sessionId); }
}
