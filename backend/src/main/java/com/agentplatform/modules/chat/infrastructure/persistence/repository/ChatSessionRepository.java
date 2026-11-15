package com.agentplatform.modules.chat.infrastructure.persistence.repository;

import com.agentplatform.modules.chat.infrastructure.persistence.mapper.ChatSessionMapper;

import com.agentplatform.modules.chat.domain.model.ChatSession;
import com.agentplatform.modules.chat.domain.repository.SessionRepository;
import com.agentplatform.modules.chat.infrastructure.persistence.entity.ChatSessionEntity;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 会话仓储的 MyBatis-Plus 实现（infrastructure 层）。
 *
 * <p>在领域端口之上包装 Mapper，负责实体与领域模型互转；同时为运营指标
 * 提供按创建时间窗口查询的读模型能力（不属于领域端口）。</p>
 */
@Repository
public class ChatSessionRepository implements SessionRepository {
    private final ChatSessionMapper mapper;

    public ChatSessionRepository(ChatSessionMapper mapper) { this.mapper = mapper; }

    /**
     * 查询全部会话并转换为领域模型。
     *
     * @return 会话列表
     */
    @Override public List<ChatSession> findAll() { return mapper.selectList(null).stream().map(ChatSessionEntity::toDomain).toList(); }

    /**
     * 按 ID 查询会话并转换为领域模型。
     *
     * @param id 会话 ID
     * @return 命中的会话；不存在时为空 Optional
     */
    @Override public Optional<ChatSession> findById(String id) { return Optional.ofNullable(mapper.selectById(id)).map(ChatSessionEntity::toDomain); }

    /**
     * 保存会话：主键已存在则更新，否则插入。
     *
     * @param session 领域会话
     */
    @Override public void save(ChatSession session) { mapper.insertOrUpdate(ChatSessionEntity.from(session)); }

    /**
     * 按 ID 删除会话。
     *
     * @param id 会话 ID
     */
    @Override public void deleteById(String id) { mapper.deleteById(id); }

    /**
     * 供运营概览/智能体指标使用的读模型。
     *
     * @param from 起始时间（含）
     * @param to   结束时间（不含）
     * @return 会话实体列表
     */
    public List<ChatSessionEntity> findByCreatedAtBetween(Instant from, Instant to) {
        return mapper.findByCreatedAtBetween(from, to);
    }
}
