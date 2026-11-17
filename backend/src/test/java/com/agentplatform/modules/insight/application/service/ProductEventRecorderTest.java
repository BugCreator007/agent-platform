package com.agentplatform.modules.insight.application.service;

import com.agentplatform.common.error.BusinessException;
import com.agentplatform.common.error.ErrorCode;
import com.agentplatform.modules.insight.domain.model.ProductEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;

/**
 * 埋点记录器单元测试：客户端白名单、属性过滤、失败降级与幂等。
 */
@ExtendWith(MockitoExtension.class)
class ProductEventRecorderTest {
    @Mock private ProductEventWriter writer;
    @Mock private ClientEventRateLimiter rateLimiter;

    private final Executor direct = Runnable::run;
    private SimpleMeterRegistry meters;
    private ProductEventRecorder recorder;

    @BeforeEach
    void setUp() {
        meters = new SimpleMeterRegistry();
        recorder = new ProductEventRecorder(writer, direct, new ObjectMapper(), rateLimiter, meters, true);
    }

    /** answer_completed 含检索诊断共 11 个属性，必须能正常写入。 */
    @Test
    void answerCompletedPropertiesFit() {
        recorder.answerCompleted("alice", "s1", "a1", "m1", "ANSWER", 120, 10, "demo", false,
                2, 1, 3, 0.8, "vector", false);
        ArgumentCaptor<ProductEvent> captor = ArgumentCaptor.forClass(ProductEvent.class);
        verify(writer).write(captor.capture());
        assertEquals("answer_completed", captor.getValue().eventName());
        assertEquals(11, captor.getValue().properties().size());
        assertEquals(3, captor.getValue().properties().get("hitCount"));
    }

    /** 客户端属性按事件白名单过滤，原文类字段不会进入事件。 */
    @Test
    void clientPropertiesAreFiltered() {
        recorder.recordClientEvent("answer_copied", "evt_1", "alice", "s1", "m1", "a1",
                Map.of("length", 12, "content", "敏感原文"));

        ArgumentCaptor<ProductEvent> captor = ArgumentCaptor.forClass(ProductEvent.class);
        verify(writer).write(captor.capture());
        ProductEvent event = captor.getValue();
        assertEquals("answer_copied", event.eventName());
        assertEquals(Set.of("length"), event.properties().keySet());
        assertEquals("alice", event.userId());
    }

    /** 客户端不能上报服务端事件。 */
    @Test
    void clientCannotReportServerEvent() {
        BusinessException error = assertThrows(BusinessException.class, () -> recorder.recordClientEvent(
                "answer_completed", "evt_1", "alice", null, null, null, Map.of()));
        assertEquals(ErrorCode.VALIDATION_ERROR, error.errorCode());
        verify(writer, never()).write(any());
    }

    /** 非标量属性被拒绝。 */
    @Test
    void nestedPropertyRejected() {
        assertThrows(BusinessException.class, () -> recorder.recordClientEvent(
                "answer_copied", "evt_1", "alice", "s1", "m1", "a1",
                Map.of("length", Map.of("nested", 1))));
    }

    /** 写入失败只记录指标，不向调用方抛异常。 */
    @Test
    void writeFailureIsSwallowed() {
        doThrow(new RuntimeException("db down")).when(writer).write(any());
        assertDoesNotThrow(() -> recorder.sessionStarted("alice", "s1", "a1", "user_portal"));
        assertEquals(1.0, meters.counter("insight_event_record_failed_total", "event", "session_started").count());
    }

    /** 重复 eventId 视为幂等，不记失败。 */
    @Test
    void duplicateEventIdIsIdempotent() {
        doThrow(new DuplicateKeyException("dup")).when(writer).write(any());
        assertDoesNotThrow(() -> recorder.sessionStarted("alice", "s1", "a1", "user_portal"));
        assertEquals(0.0, meters.counter("insight_event_record_failed_total", "event", "session_started").count());
    }

    /** 开关关闭时完全不写。 */
    @Test
    void disabledRecorderWritesNothing() {
        ProductEventRecorder disabled = new ProductEventRecorder(writer, direct, new ObjectMapper(),
                rateLimiter, meters, false);
        disabled.sessionStarted("alice", "s1", "a1", "user_portal");
        verify(writer, never()).write(any());
    }
}
