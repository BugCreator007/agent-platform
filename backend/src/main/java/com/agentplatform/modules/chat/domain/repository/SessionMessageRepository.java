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
}
