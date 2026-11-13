package com.agentplatform.modules.chat.infrastructure.persistence;

import com.agentplatform.modules.chat.infrastructure.persistence.entity.SessionMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SessionMessageJpaRepository extends JpaRepository<SessionMessageEntity, String> {
    List<SessionMessageEntity> findBySessionIdOrderByCreatedAtAsc(String sessionId);
    void deleteBySessionId(String sessionId);
}
