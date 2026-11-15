package com.agentplatform.modules.chat.domain.repository;

import com.agentplatform.modules.chat.domain.model.ChatSession;

import java.util.List;
import java.util.Optional;

/**
 * 会话聚合仓储端口（domain 层）。
 *
 * <p>由 infrastructure 层基于 MyBatis-Plus 实现；领域/应用层只依赖本接口。</p>
 */
public interface SessionRepository {
    /**
     * 查询全部会话，未做分页与排序。
     *
     * @return 会话列表；无数据时为空列表
     */
    List<ChatSession> findAll();

    /**
     * 按 ID 查询会话。
     *
     * @param id 会话 ID
     * @return 命中的会话；不存在时为空 Optional
     */
    Optional<ChatSession> findById(String id);

    /**
     * 保存会话（不存在则插入，存在则全量更新汇总字段）。
     *
     * @param session 待保存的会话聚合
     */
    void save(ChatSession session);

    /**
     * 按 ID 删除会话。
     *
     * @param id 会话 ID
     */
    void deleteById(String id);
}
