package com.agentplatform.modules.chat.infrastructure.persistence.repository;

import com.agentplatform.modules.chat.domain.model.MessageFeedback;
import com.agentplatform.modules.chat.domain.repository.FeedbackRepository;
import com.agentplatform.modules.chat.infrastructure.persistence.entity.MessageFeedbackEntity;
import com.agentplatform.modules.chat.infrastructure.persistence.mapper.MessageFeedbackMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 消息反馈仓储的 MyBatis-Plus 实现（infrastructure 层）。
 *
 * <p>按 {@code (message_id, user_id)} 唯一约束写入；并发重复提交触发唯一键冲突时，
 * 回退为更新既有记录，保证接口幂等。</p>
 */
@Repository
public class FeedbackRepositoryImpl implements FeedbackRepository {
    private final MessageFeedbackMapper mapper;

    /**
     * @param mapper 反馈表 Mapper
     */
    public FeedbackRepositoryImpl(MessageFeedbackMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 按消息与反馈人查询，唯一约束保证最多一条。
     *
     * @param messageId 消息 ID
     * @param userId    反馈人
     * @return 命中的反馈
     */
    @Override
    public Optional<MessageFeedback> findByMessageAndUser(String messageId, String userId) {
        List<MessageFeedbackEntity> rows = mapper.selectList(new LambdaQueryWrapper<MessageFeedbackEntity>()
                .eq(MessageFeedbackEntity::getMessageId, messageId)
                .eq(MessageFeedbackEntity::getUserId, userId));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0).toDomain());
    }

    /**
     * 查询某会话中某用户的全部反馈。
     *
     * @param sessionId 会话 ID
     * @param userId    反馈人
     * @return 反馈列表
     */
    @Override
    public List<MessageFeedback> findBySessionAndUser(String sessionId, String userId) {
        return mapper.selectList(new LambdaQueryWrapper<MessageFeedbackEntity>()
                        .eq(MessageFeedbackEntity::getSessionId, sessionId)
                        .eq(MessageFeedbackEntity::getUserId, userId))
                .stream().map(MessageFeedbackEntity::toDomain).toList();
    }

    /**
     * 按时间窗口查询反馈。
     *
     * @param agentId 智能体 ID，可为空
     * @param rating  评分，可为空
     * @param from    起始时间（含）
     * @return 反馈列表
     */
    @Override
    public List<MessageFeedback> find(String agentId, String rating, Instant from) {
        return mapper.findFeedback(agentId, rating, from).stream()
                .map(MessageFeedbackEntity::toDomain).toList();
    }

    /**
     * 分页查询反馈。
     *
     * @param agentId    智能体 ID，可为空
     * @param rating     评分，可为空
     * @param reasonCode 原因编码，可为空
     * @param from       起始时间（含）
     * @param to         截止时间（不含）
     * @param offset     偏移量
     * @param size       每页条数
     * @return 当前页记录
     */
    @Override
    public List<MessageFeedback> findPage(String agentId, String rating, String reasonCode,
                                          Instant from, Instant to, int offset, int size) {
        return mapper.findFeedbackPage(agentId, rating, reasonCode, from, to, offset, size).stream()
                .map(MessageFeedbackEntity::toDomain).toList();
    }

    /**
     * 统计过滤条件下的反馈总数。
     *
     * @param agentId    智能体 ID，可为空
     * @param rating     评分，可为空
     * @param reasonCode 原因编码，可为空
     * @param from       起始时间（含）
     * @param to         截止时间（不含）
     * @return 总条数
     */
    @Override
    public long count(String agentId, String rating, String reasonCode, Instant from, Instant to) {
        return mapper.countFeedback(agentId, rating, reasonCode, from, to);
    }

    /**
     * 保存反馈：主键不存在则插入；唯一键冲突时复用既有主键更新。
     *
     * @param feedback 反馈记录
     */
    @Override
    public void save(MessageFeedback feedback) {
        MessageFeedbackEntity entity = MessageFeedbackEntity.from(feedback);
        try {
            mapper.insertOrUpdate(entity);
        } catch (DuplicateKeyException e) {
            // 并发重复提交：唯一键 (message_id, user_id) 已存在，改为更新既有记录
            List<MessageFeedbackEntity> existing = mapper.selectList(new LambdaQueryWrapper<MessageFeedbackEntity>()
                    .eq(MessageFeedbackEntity::getMessageId, feedback.messageId())
                    .eq(MessageFeedbackEntity::getUserId, feedback.userId()));
            if (existing.isEmpty()) throw e;
            entity.setId(existing.get(0).getId());
            mapper.updateById(entity);
        }
    }

    /**
     * 删除某用户对某条消息的反馈；不存在时静默返回，保证撤销幂等。
     *
     * @param messageId 消息 ID
     * @param userId    反馈人
     */
    @Override
    public void deleteByMessageAndUser(String messageId, String userId) {
        mapper.delete(new LambdaQueryWrapper<MessageFeedbackEntity>()
                .eq(MessageFeedbackEntity::getMessageId, messageId)
                .eq(MessageFeedbackEntity::getUserId, userId));
    }
}
