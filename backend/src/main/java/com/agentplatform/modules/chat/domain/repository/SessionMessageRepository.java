package com.agentplatform.modules.chat.domain.repository;

import com.agentplatform.modules.chat.domain.model.SessionMessage;

import java.util.List;

/**
 * 会话消息仓储端口（domain 层）。
 *
 * <p>消息按创建时间升序读取，供运行时截取最近历史与前端回放对话。</p>
 */
public interface SessionMessageRepository {
    /**
     * 保存消息（不存在则插入，存在则更新）。
     *
     * @param message 待保存的消息
     */
    void save(SessionMessage message);

    /**
     * 按消息 ID 查询单条消息。
     *
     * @param id 消息 ID
     * @return 命中的消息；不存在时为空
     */
    java.util.Optional<SessionMessage> findById(String id);

    /**
     * 查询某会话的全部消息，按创建时间升序。
     *
     * @param sessionId 会话 ID
     * @return 消息列表；无数据时为空列表
     */
    List<SessionMessage> findBySession(String sessionId);

    /**
     * 删除某会话的全部消息（会话删除时级联清理）。
     *
     * @param sessionId 会话 ID
     */
    void deleteBySession(String sessionId);

    /**
     * 统计时间窗口内的助手消息数，用于反馈率与无答案率。
     *
     * @param from    起始时间（含）
     * @param agentId 智能体 ID，可为空
     * @return 助手消息数
     */
    long countAssistant(java.time.Instant from, String agentId);

    /**
     * 统计时间窗口内的拒答消息数。
     *
     * @param from    起始时间（含）
     * @param agentId 智能体 ID，可为空
     * @return 拒答消息数
     */
    long countRefusal(java.time.Instant from, String agentId);
}
