package com.agentplatform.modules.chat.application.service;

import com.agentplatform.common.error.BusinessException;
import com.agentplatform.common.error.ErrorCode;
import com.agentplatform.modules.chat.domain.model.ChatSession;
import com.agentplatform.modules.chat.domain.repository.SessionMessageRepository;
import com.agentplatform.modules.chat.domain.repository.SessionRepository;
import com.agentplatform.modules.chat.domain.model.SessionMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * 会话管理应用服务（application 层）。
 *
 * <p>提供后台会话列表/详情/消息查询与删除；列表过滤在内存中完成，
 * 删除时先级联清理消息再删除会话，整体在同一事务内执行。</p>
 */
@Service
public class SessionApplicationService {
    private final SessionRepository repository;
    private final SessionMessageRepository messages;

    public SessionApplicationService(SessionRepository repository, SessionMessageRepository messages) {
        this.repository = repository;
        this.messages = messages;
    }

    /**
     * 查询会话列表，支持状态、智能体与关键字过滤，结果按展示时间倒序。
     *
     * @param status  会话状态过滤；为 null 或空白时不过滤
     * @param agentId 智能体 ID 过滤；为 null 或空白时不过滤
     * @param q       关键字，匹配主题或会话 ID；为 null 或空白时不过滤
     * @return 过滤并排序后的会话列表
     */
    public List<ChatSession> list(String status, String agentId, String q) {
        return repository.findAll().stream()
                .filter(s -> status == null || status.isBlank() || s.status().equalsIgnoreCase(status))
                .filter(s -> agentId == null || agentId.isBlank() || s.agentId().equals(agentId))
                .filter(s -> q == null || q.isBlank() || s.topic().contains(q) || s.id().contains(q))
                .sorted(Comparator.comparing(ChatSession::time).reversed())
                .toList();
    }

    /**
     * 按 ID 获取会话。
     *
     * @param id 会话 ID
     * @return 会话聚合
     * @throws BusinessException 会话不存在时抛出 {@link ErrorCode#NOT_FOUND}
     */
    public ChatSession get(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "会话不存在: " + id));
    }

    /**
     * 删除会话及其全部消息。
     *
     * <p>先校验会话存在，再删除消息、最后删除会话；任一失败由事务整体回滚，
     * 避免出现“消息已删、会话仍在”的悬挂数据。</p>
     *
     * @param id 会话 ID
     * @throws BusinessException 会话不存在时抛出 {@link ErrorCode#NOT_FOUND}
     */
    @Transactional
    public void delete(String id) {
        get(id);
        messages.deleteBySession(id);
        repository.deleteById(id);
    }

    /**
     * 查询会话的消息明细，按创建时间升序。
     *
     * @param id 会话 ID
     * @return 消息列表；无消息时为空列表
     * @throws BusinessException 会话不存在时抛出 {@link ErrorCode#NOT_FOUND}
     */
    public List<SessionMessage> messages(String id) {
        get(id);
        return messages.findBySession(id);
    }
}
