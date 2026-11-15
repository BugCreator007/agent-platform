package com.agentplatform.modules.chat.infrastructure.persistence.repository;

import com.agentplatform.modules.chat.infrastructure.persistence.mapper.SessionMessageMapper;

import com.agentplatform.modules.chat.domain.model.SessionMessage;
import com.agentplatform.modules.chat.infrastructure.persistence.entity.SessionMessageEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 会话消息仓储的 MyBatis-Plus 实现（infrastructure 层）。
 *
 * <p>实现 domain 层 {@code SessionMessageRepository} 端口，负责实体与领域模型互转。</p>
 */
@Repository
public class SessionMessageRepository implements com.agentplatform.modules.chat.domain.repository.SessionMessageRepository {
    private final SessionMessageMapper mapper;

    public SessionMessageRepository(SessionMessageMapper mapper) { this.mapper = mapper; }

    /**
     * 保存消息：主键已存在则更新，否则插入。
     *
     * @param message 领域消息
     */
    @Override public void save(SessionMessage message) { mapper.insertOrUpdate(SessionMessageEntity.from(message)); }

    /**
     * 查询某会话的全部消息并转换为领域模型，按创建时间升序。
     *
     * @param sessionId 会话 ID
     * @return 消息列表
     */
    @Override public List<SessionMessage> findBySession(String sessionId) {
        return mapper.findBySessionIdOrderByCreatedAtAsc(sessionId).stream().map(SessionMessageEntity::toDomain).toList();
    }

    /**
     * 删除某会话的全部消息；与会话删除同处一个事务。
     *
     * @param sessionId 会话 ID
     */
    @Override @Transactional public void deleteBySession(String sessionId) { mapper.deleteBySessionId(sessionId); }
}
