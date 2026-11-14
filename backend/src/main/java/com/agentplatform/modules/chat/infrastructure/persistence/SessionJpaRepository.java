package com.agentplatform.modules.chat.infrastructure.persistence;

import com.agentplatform.modules.chat.infrastructure.persistence.entity.ChatSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface SessionJpaRepository extends JpaRepository<ChatSessionEntity, String> {
    List<ChatSessionEntity> findByCreatedAtBetweenOrderByCreatedAtAsc(Instant from, Instant to);
}
