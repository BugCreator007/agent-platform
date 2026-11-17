package com.agentplatform.modules.chat.application.runtime;

import com.agentplatform.modules.agent.application.query.AgentSummary;
import com.agentplatform.modules.agent.application.service.AgentApplicationService;
import com.agentplatform.modules.chat.domain.model.ChatSession;
import com.agentplatform.modules.chat.domain.model.SessionMessage;
import com.agentplatform.modules.chat.domain.repository.SessionMessageRepository;
import com.agentplatform.modules.chat.domain.repository.SessionRepository;
import com.agentplatform.modules.chat.infrastructure.ai.ModelGateway;
import com.agentplatform.modules.insight.application.service.ProductEventRecorder;
import com.agentplatform.modules.knowledge.application.query.RetrievalResult;
import com.agentplatform.modules.knowledge.application.service.KnowledgeApplicationService;
import com.agentplatform.modules.tool.application.service.McpRuntimeService;
import com.agentplatform.modules.tool.application.service.ToolExecutionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 严格引用模式运行时测试：知识库 0 命中时必须直接拒答，且不调用模型。
 */
@ExtendWith(MockitoExtension.class)
class AgentRuntimeServiceStrictModeTest {
    @Mock private AgentApplicationService agents;
    @Mock private SessionRepository sessions;
    @Mock private SessionMessageRepository messages;
    @Mock private ModelGateway model;
    @Mock private KnowledgeApplicationService knowledge;
    @Mock private ToolExecutionService tools;
    @Mock private McpRuntimeService mcp;
    @Mock private ProductEventRecorder events;

    /** 0 命中时生成 REFUSAL 消息，并保证模型流与工具链均未触发。 */
    @Test
    void strictModeRefusesWithoutCallingModel() {
        AgentSummary agent = new AgentSummary("a1", "知识问答助手", "kb-qa", "running", "v1",
                "演示模型", null, null, "产品", "知识运营", "全量", 0, 0, "—", 0, "刚刚",
                "你是知识助手", List.of(), List.of("kb_1"), List.of("kb_1"), "STRICT");
        ChatSession session = new ChatSession("s_1", "a1", "知识问答助手", "running", 0, 0, 0,
                0, "10:00:00", "问题", List.of(), "viewer");
        when(agents.get("a1")).thenReturn(agent);
        when(sessions.findById("s_1")).thenReturn(Optional.of(session));
        when(messages.findBySession("s_1")).thenReturn(List.of());
        when(knowledge.retrieve("kb_1", "任何问题"))
                .thenReturn(new RetrievalResult("任何问题", 1, "vector", 5, 0.8, List.of()));
        when(model.routes(null, null)).thenReturn(List.of());
        when(model.configured(anyList())).thenReturn(false);
        when(model.providerLabel(anyList())).thenReturn("演示");
        when(model.routeLabel(anyList())).thenReturn("演示");

        AgentRuntimeService service = new AgentRuntimeService(agents, sessions, messages, model,
                knowledge, tools, mcp, new ObjectMapper(), events, new SimpleMeterRegistry());
        service.stream(new RuntimeChatRequest("a1", "任何问题", "s_1"), "viewer");

        ArgumentCaptor<SessionMessage> captor = ArgumentCaptor.forClass(SessionMessage.class);
        verify(messages, times(2)).save(captor.capture());
        SessionMessage assistant = captor.getAllValues().stream()
                .filter(m -> "assistant".equals(m.role())).findFirst().orElseThrow();
        assertEquals("REFUSAL", assistant.answerType());
        assertFalse(assistant.lowConfidence());
        assertTrue(assistant.content().contains("没有找到"));
        // 严格模式不得调用模型或工具
        verify(model, never()).stream(any(), anyString(), anyList());
        verify(model, never()).streamWithTools(any(), anyString(), anyList(), anyList());
        verify(tools, never()).execute(anyString(), anyString());
        verify(mcp, never()).bindings(anyList());
    }
}
