package com.agentplatform.modules.chat.infrastructure.persistence.repository;

import com.agentplatform.modules.chat.infrastructure.persistence.mapper.ChatSessionMapper;

import com.agentplatform.modules.chat.domain.model.ChatSession;
import com.agentplatform.modules.chat.domain.repository.SessionRepository;
import com.agentplatform.modules.chat.infrastructure.persistence.entity.ChatSessionEntity;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class ChatSessionRepository implements SessionRepository {
    private final ChatSessionMapper mapper;

    public ChatSessionRepository(ChatSessionMapper mapper) { this.mapper = mapper; }

    @Override public List<ChatSession> findAll() { return mapper.selectList(null).stream().map(ChatSessionEntity::toDomain).toList(); }
    @Override public Optional<ChatSession> findById(String id) { return Optional.ofNullable(mapper.selectById(id)).map(ChatSessionEntity::toDomain); }
    @Override public void save(ChatSession session) { mapper.insertOrUpdate(ChatSessionEntity.from(session)); }
    @Override public void deleteById(String id) { mapper.deleteById(id); }

    /** 供运营概览/智能体指标使用的读模型。 */
    public List<ChatSessionEntity> findByCreatedAtBetween(Instant from, Instant to) {
        return mapper.findByCreatedAtBetween(from, to);
    }
}
