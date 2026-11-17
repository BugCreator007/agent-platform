package com.agentplatform.modules.chat.application.service;

import com.agentplatform.common.error.BusinessException;
import com.agentplatform.common.error.ErrorCode;
import com.agentplatform.common.security.UserContext;
import com.agentplatform.modules.chat.domain.model.ChatSession;
import com.agentplatform.modules.chat.domain.repository.SessionMessageRepository;
import com.agentplatform.modules.chat.domain.repository.SessionRepository;
import com.agentplatform.modules.chat.domain.model.SessionMessage;
import com.agentplatform.modules.chat.application.query.CitationView;
import com.agentplatform.modules.chat.application.query.UserMessageView;
import com.agentplatform.modules.insight.application.service.ProductEventRecorder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * 会话管理应用服务（application 层）。
 *
 * <p>提供后台会话列表/详情/消息查询与删除；所有读操作先经过
 * {@link SessionAccessPolicy} 的归属判定，普通用户只能看到本人会话，
 * 历史无主会话仅管理员/开发者可见。删除时先级联清理消息再删除会话，
 * 整体在同一事务内执行。</p>
 */
@Service
public class SessionApplicationService {
    private final SessionRepository repository;
    private final SessionMessageRepository messages;
    private final ProductEventRecorder events;

    public SessionApplicationService(SessionRepository repository, SessionMessageRepository messages,
                                     ProductEventRecorder events) {
        this.repository = repository;
        this.messages = messages;
        this.events = events;
    }

    /**
     * 查询当前用户可见的会话列表，支持状态、智能体与关键字过滤。
     *
     * @param user    当前用户
     * @param status  会话状态过滤；为 null 或空白时不过滤
     * @param agentId 智能体 ID 过滤；为 null 或空白时不过滤
     * @param q       关键字，匹配主题或会话 ID；为 null 或空白时不过滤
     * @return 过滤并排序后的会话列表
     */
    public List<ChatSession> list(UserContext user, String status, String agentId, String q) {
        return repository.findAll().stream()
                .filter(s -> SessionAccessPolicy.canAccess(user, s))
                .filter(s -> status == null || status.isBlank() || s.status().equalsIgnoreCase(status))
                .filter(s -> agentId == null || agentId.isBlank() || s.agentId().equals(agentId))
                .filter(s -> q == null || q.isBlank() || s.topic().contains(q) || s.id().contains(q))
                .sorted(Comparator.comparing(ChatSession::time).reversed())
                .toList();
    }

    /**
     * 按 ID 获取当前用户有权访问的会话。
     *
     * @param user 当前用户
     * @param id   会话 ID
     * @return 会话聚合
     * @throws BusinessException 会话不存在或无权访问时统一抛出 {@link ErrorCode#NOT_FOUND}
     */
    public ChatSession get(UserContext user, String id) {
        ChatSession session = find(id);
        // 非本人资源统一按不存在处理，避免向调用方泄露会话是否存在。
        if (!SessionAccessPolicy.canAccess(user, session)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "会话不存在: " + id);
        }
        return session;
    }

    /**
     * 校验当前用户可访问指定会话；供 portal 的事件接口使用，不向 portal 暴露领域对象。
     *
     * @param user 当前用户
     * @param id   会话 ID
     * @throws BusinessException 会话不存在或无权访问时抛出 {@link ErrorCode#NOT_FOUND}
     */
    public void ensureAccessible(UserContext user, String id) {
        get(user, id);
    }

    /**
     * 校验消息属于指定会话，供客户端事件接口使用。
     *
     * <p>先校验会话归属，再确认消息存在且属于该会话；任一不满足统一 404，
     * 避免把采纳事件挂到他人消息上污染口径。</p>
     *
     * @param user      当前用户
     * @param sessionId 会话 ID
     * @param messageId 消息 ID
     * @throws BusinessException 会话或消息不存在、不属于该会话时抛出 {@link ErrorCode#NOT_FOUND}
     */
    public void ensureMessageInSession(UserContext user, String sessionId, String messageId) {
        ensureAccessible(user, sessionId);
        messages.findById(messageId)
                .filter(message -> sessionId.equals(message.sessionId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "消息不存在"));
    }

    /**
     * 查询用户端历史消息视图，字段与 user-web 契约对齐。
     *
     * @param user 当前用户
     * @param id   会话 ID
     * @return 消息视图列表
     */
    public List<UserMessageView> messagesForUser(UserContext user, String id) {
        return messages(user, id).stream()
                .map(message -> new UserMessageView(message.id(), message.sessionId(), message.role(),
                        message.content(), message.tokens(), message.model(), message.contextCount(),
                        message.createdAt(),
                        message.citations() == null ? List.of() : message.citations().stream()
                                .map(c -> new CitationView(c.documentId(), c.name(), c.url(), c.sourceType()))
                                .toList(),
                        message.answerType(), message.lowConfidence()))
                .toList();
    }

    /**
     * 删除会话及其全部消息，仅由管理端接口在管理员权限下调用。
     *
     * <p>先校验会话存在，再删除消息、最后删除会话；任一失败由事务整体回滚，
     * 避免出现“消息已删、会话仍在”的悬挂数据。</p>
     *
     * @param id    会话 ID
     * @param actor 操作人登录名，用于埋点；内部调用可为空
     * @throws BusinessException 会话不存在时抛出 {@link ErrorCode#NOT_FOUND}
     */
    @Transactional
    public void delete(String id, String actor) {
        ChatSession session = find(id);
        messages.deleteBySession(id);
        repository.deleteById(id);
        events.sessionDeleted(actor, id, session.agentId(), session.turns());
    }

    /**
     * 查询会话的消息明细，按创建时间升序。
     *
     * @param user 当前用户
     * @param id   会话 ID
     * @return 消息列表；无消息时为空列表
     * @throws BusinessException 会话不存在或无权访问时抛出 {@link ErrorCode#NOT_FOUND}
     */
    public List<SessionMessage> messages(UserContext user, String id) {
        get(user, id);
        return messages.findBySession(id);
    }

    /**
     * 按 ID 读取会话，不做权限判断；仅供本服务内部与管理员删除使用。
     *
     * @param id 会话 ID
     * @return 会话聚合
     * @throws BusinessException 会话不存在时抛出 {@link ErrorCode#NOT_FOUND}
     */
    private ChatSession find(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "会话不存在: " + id));
    }
}
