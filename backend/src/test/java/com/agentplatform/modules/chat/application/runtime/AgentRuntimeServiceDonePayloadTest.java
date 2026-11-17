package com.agentplatform.modules.chat.application.runtime;

import com.agentplatform.modules.agent.application.query.AgentSummary;
import com.agentplatform.modules.chat.domain.model.MessageCitation;
import com.agentplatform.modules.chat.domain.model.RetrievalDiagnostics;
import com.agentplatform.modules.chat.infrastructure.ai.ModelGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * done 事件契约测试：普通、MCP 与拒答三条分支使用同一构造器，字段保持一致。
 */
@ExtendWith(MockitoExtension.class)
class AgentRuntimeServiceDonePayloadTest {
    @Mock private ModelGateway model;

    private AgentRuntimeService service;
    private AgentSummary agent;

    @BeforeEach
    void setUp() {
        when(model.providerLabel(anyList())).thenReturn("演示");
        when(model.routeLabel(anyList())).thenReturn("演示");
        when(model.configured(anyList())).thenReturn(true);
        service = new AgentRuntimeService(null, null, null, model, null, null, null,
                new ObjectMapper(), null, new SimpleMeterRegistry());
        agent = new AgentSummary("a1", "助手", "assistant", "running", "v1", "模型",
                null, null, "产品", "团队", "全量", 0, 0, "—", 0, "刚刚",
                "提示词", List.of(), List.of(), List.of("kb"), "HYBRID");
    }

    /** 普通/MCP 回答必须包含消息 ID、回答类型、低置信与检索诊断，MCP 额外带工具数。 */
    @Test
    void answerContractIncludesRequiredFields() {
        RetrievalDiagnostics retrieval = new RetrievalDiagnostics(2, 0.82, "vector", false);
        Map<String, Object> done = service.donePayload("s1", agent, List.of(), "模型", 2, 3,
                10, 100, false, List.<MessageCitation>of(), "msg_1", "ANSWER", true, retrieval, null);

        assertEquals("s1", done.get("sessionId"));
        assertEquals("msg_1", done.get("assistantMessageId"));
        assertEquals("ANSWER", done.get("answerType"));
        assertEquals(true, done.get("lowConfidence"));
        assertEquals(retrieval, done.get("retrieval"));
        assertEquals(3, done.get("tools"));
        assertNull(done.get("content"));
    }

    /** 拒答分支必须携带固定文案，且低置信为 false。 */
    @Test
    void refusalContractIncludesContent() {
        RetrievalDiagnostics retrieval = new RetrievalDiagnostics(0, 0, "hybrid", true);
        Map<String, Object> done = service.donePayload("s1", agent, List.of(), "模型", 0, 0,
                0, 100, false, List.<MessageCitation>of(), "msg_1", "REFUSAL", false, retrieval,
                "知识库里没有找到答案");

        assertEquals("REFUSAL", done.get("answerType"));
        assertFalse((Boolean) done.get("lowConfidence"));
        assertEquals("知识库里没有找到答案", done.get("content"));
        assertNotNull(done.get("retrieval"));
    }
}
