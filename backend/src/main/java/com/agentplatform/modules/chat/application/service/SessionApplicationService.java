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

@Service
public class SessionApplicationService {
    private final SessionRepository repository;
    private final SessionMessageRepository messages;

    public SessionApplicationService(SessionRepository repository, SessionMessageRepository messages) {
        this.repository = repository;
        this.messages = messages;
    }

    public List<ChatSession> list(String status, String agentId, String q) {
        return repository.findAll().stream()
                .filter(s -> status == null || status.isBlank() || s.status().equalsIgnoreCase(status))
                .filter(s -> agentId == null || agentId.isBlank() || s.agentId().equals(agentId))
                .filter(s -> q == null || q.isBlank() || s.topic().contains(q) || s.id().contains(q))
                .sorted(Comparator.comparing(ChatSession::time).reversed())
                .toList();
    }

    public ChatSession get(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "会话不存在: " + id));
    }

    @Transactional
    public void delete(String id) {
        get(id);
        messages.deleteBySession(id);
        repository.deleteById(id);
    }

    public List<SessionMessage> messages(String id) {
        get(id);
        return messages.findBySession(id);
    }
}
