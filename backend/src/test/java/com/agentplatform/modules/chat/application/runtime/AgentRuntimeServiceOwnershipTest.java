package com.agentplatform.modules.chat.application.runtime;

import com.agentplatform.common.error.BusinessException;
import com.agentplatform.common.error.ErrorCode;
import com.agentplatform.common.security.UserContext;
import com.agentplatform.modules.agent.application.query.AgentSummary;
import com.agentplatform.modules.agent.application.service.AgentApplicationService;
import com.agentplatform.modules.chat.domain.model.ChatSession;
import com.agentplatform.modules.chat.domain.repository.SessionMessageRepository;
import com.agentplatform.modules.chat.domain.repository.SessionRepository;
import com.agentplatform.modules.chat.infrastructure.ai.ModelGateway;
import com.agentplatform.modules.insight.application.service.ProductEventRecorder;
import com.agentplatform.modules.knowledge.application.service.KnowledgeApplicationService;
import com.agentplatform.modules.tool.application.service.McpRuntimeService;
import com.agentplatform.modules.tool.application.service.ToolExecutionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * 运行时会话归属测试：续聊必须校验 created_by，历史无主会话普通用户不可续聊。
 */
@ExtendWith(MockitoExtension.class)
class AgentRuntimeServiceOwnershipTest {
    @Mock private AgentApplicationService agents;
    @Mock private SessionRepository sessions;
    @Mock private SessionMessageRepository messages;
    @Mock private ModelGateway model;
    @Mock private KnowledgeApplicationService knowledge;
    @Mock private ToolExecutionService tools;
    @Mock private McpRuntimeService mcp;
    @Mock private ProductEventRecorder events;

    /** 普通用户续聊他人会话返回 404。 */
    @Test
    void otherUsersSessionRejected() {
        when(agents.get("a1")).thenReturn(agent());
        when(sessions.findById("s_bob")).thenReturn(Optional.of(session("bob")));
        AgentRuntimeService service = service();

        BusinessException error = assertThrows(BusinessException.class, () -> service.stream(
                new RuntimeChatRequest("a1", "继续", "s_bob"),
                new UserContext("alice", "u_alice", "Alice", List.of("VIEWER"))));
        assertEquals(ErrorCode.NOT_FOUND, error.errorCode());
    }

    /** 历史无主会话普通用户不可续聊。 */
    @Test
    void legacySessionRejectedForNormalUser() {
        when(agents.get("a1")).thenReturn(agent());
        when(sessions.findById("s_legacy")).thenReturn(Optional.of(session(null)));
        AgentRuntimeService service = service();

        BusinessException error = assertThrows(BusinessException.class, () -> service.stream(
                new RuntimeChatRequest("a1", "继续", "s_legacy"),
                new UserContext("alice", "u_alice", "Alice", List.of("VIEWER"))));
        assertEquals(ErrorCode.NOT_FOUND, error.errorCode());
    }

    private AgentRuntimeService service() {
        return new AgentRuntimeService(agents, sessions, messages, model, knowledge, tools, mcp,
                new ObjectMapper(), events, new SimpleMeterRegistry());
    }

    private AgentSummary agent() {
        return new AgentSummary("a1", "助手", "assistant", "running", "v1", "演示模型",
                null, null, "产品", "团队", "全量", 0, 0, "—", 0, "刚刚",
                "提示词", List.of(), List.of(), List.of(), "HYBRID");
    }

    private ChatSession session(String owner) {
        return new ChatSession("s_1", "a1", "助手", "success", 1, 0, 0, 10,
                "10:00:00", "问题", List.of(), owner);
    }
}
