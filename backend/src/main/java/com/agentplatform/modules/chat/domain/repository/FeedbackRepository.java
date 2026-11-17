package com.agentplatform.modules.chat.domain.repository;

import com.agentplatform.modules.chat.domain.model.MessageFeedback;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 消息反馈仓储端口（domain 层）。
 *
 * <p>按反馈人 + 消息唯一定位；管理端统计按时间窗口读取原始记录后在应用层聚合，
 * 避免把统计口径散落到 SQL 中。</p>
 */
public interface FeedbackRepository {
    /**
     * 按消息与反馈人查询。
     *
     * @param messageId 消息 ID
     * @param userId    反馈人
     * @return 命中的反馈
     */
    Optional<MessageFeedback> findByMessageAndUser(String messageId, String userId);

    /**
     * 查询某会话中某用户的全部反馈，用于前端恢复高亮状态。
     *
     * @param sessionId 会话 ID
     * @param userId    反馈人
     * @return 反馈列表
     */
    List<MessageFeedback> findBySessionAndUser(String sessionId, String userId);

    /**
     * 查询时间窗口内的反馈，可按智能体与评分过滤。
     *
     * @param agentId 智能体 ID，可为空
     * @param rating  评分，可为空
     * @param from    起始时间（含）
     * @return 按创建时间倒序的记录
     */
    List<MessageFeedback> find(String agentId, String rating, Instant from);

    /**
     * 分页查询反馈，可按智能体、评分、原因与时间窗口过滤。
     *
     * @param agentId    智能体 ID，可为空
     * @param rating     评分，可为空
     * @param reasonCode 原因编码，可为空
     * @param from       起始时间（含）
     * @param to         截止时间（不含）
     * @param offset     偏移量
     * @param size       每页条数
     * @return 当前页记录，按创建时间倒序
     */
    List<MessageFeedback> findPage(String agentId, String rating, String reasonCode,
                                   Instant from, Instant to, int offset, int size);

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
    long count(String agentId, String rating, String reasonCode, Instant from, Instant to);

    /**
     * 保存或更新反馈。
     *
     * @param feedback 反馈记录
     */
    void save(MessageFeedback feedback);

    /**
     * 删除某用户对某条消息的反馈。
     *
     * @param messageId 消息 ID
     * @param userId    反馈人
     */
    void deleteByMessageAndUser(String messageId, String userId);
}
