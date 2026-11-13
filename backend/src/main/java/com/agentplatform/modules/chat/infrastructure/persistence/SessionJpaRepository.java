package com.agentplatform.modules.chat.infrastructure.persistence;

import com.agentplatform.modules.chat.infrastructure.persistence.entity.ChatSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionJpaRepository extends JpaRepository<ChatSessionEntity, String> {
}
