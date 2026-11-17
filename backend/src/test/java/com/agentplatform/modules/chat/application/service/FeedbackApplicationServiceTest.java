package com.agentplatform.modules.chat.application.service;

import com.agentplatform.common.error.BusinessException;
import com.agentplatform.common.error.ErrorCode;
import com.agentplatform.modules.chat.application.query.FeedbackStats;
import com.agentplatform.modules.chat.application.query.FeedbackView;
import com.agentplatform.modules.chat.domain.model.ChatSession;
import com.agentplatform.modules.chat.domain.model.FeedbackRating;
import com.agentplatform.modules.chat.domain.model.FeedbackReason;
import com.agentplatform.modules.chat.domain.model.MessageFeedback;
import com.agentplatform.modules.chat.domain.model.SessionMessage;
import com.agentplatform.modules.chat.domain.repository.FeedbackRepository;
import com.agentplatform.modules.chat.domain.repository.SessionMessageRepository;
import com.agentplatform.modules.chat.domain.repository.SessionRepository;
import com.agentplatform.modules.insight.application.service.ProductEventRecorder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 反馈应用服务单元测试：覆盖原因校验、会话归属、快照生成与质量指标计算。
 */
@ExtendWith(MockitoExtension.class)
class FeedbackApplicationServiceTest {
    @Mock private FeedbackRepository feedbacks;
    @Mock private SessionMessageRepository messages;
    @Mock private SessionRepository sessions;
    @Mock private ProductEventRecorder events;

    private FeedbackApplicationService service;

    @BeforeEach
    void setUp() {
        service = new FeedbackApplicationService(feedbacks, messages, sessions,
                new FeedbackRateLimiter(null), events, new SimpleMeterRegistry());
    }

    /** 点踩并留下问题：应落库、生成提问快照，并标记跟进。 */
    @Test
    void downFeedbackCreatesRecordWithPreview() {
        SessionMessage user = message("msg_u", "user", "已签收还能退款吗？");
        SessionMessage answer = message("msg_a", "assistant", "已签收订单可以在 7 天内申请退款。");
        when(messages.findById("msg_a")).thenReturn(Optional.of(answer));
        when(sessions.findById("s_1")).thenReturn(Optional.of(session("alice")));
        when(messages.findBySession("s_1")).thenReturn(List.of(user, answer));
        when(feedbacks.findByMessageAndUser("msg_a", "alice")).thenReturn(Optional.empty());

        FeedbackView view = service.submit("alice", "msg_a",
                new SubmitFeedbackCommand("DOWN", List.of("INCOMPLETE"), "少了申请入口", true));

        assertEquals("DOWN", view.rating());
        assertTrue(view.followUpRequested());
        ArgumentCaptor<MessageFeedback> captor = ArgumentCaptor.forClass(MessageFeedback.class);
        verify(feedbacks).save(captor.capture());
        assertEquals("已签收还能退款吗？", captor.getValue().questionPreview());
        assertEquals("alice", captor.getValue().userId());
    }

    /** 点踩不选原因属于参数错误。 */
    @Test
    void downWithoutReasonRejected() {
        when(messages.findById("msg_a")).thenReturn(Optional.of(message("msg_a", "assistant", "回答")));
        when(sessions.findById("s_1")).thenReturn(Optional.of(session("alice")));

        BusinessException error = assertThrows(BusinessException.class, () -> service.submit("alice", "msg_a",
                new SubmitFeedbackCommand("DOWN", List.of(), null, false)));
        assertEquals(ErrorCode.VALIDATION_ERROR, error.errorCode());
    }

    /** 反馈人不是会话归属人时返回 403，避免给别人的回答反馈。 */
    @Test
    void otherUsersSessionRejected() {
        when(messages.findById("msg_a")).thenReturn(Optional.of(message("msg_a", "assistant", "回答")));
        when(sessions.findById("s_1")).thenReturn(Optional.of(session("alice")));

        BusinessException error = assertThrows(BusinessException.class, () -> service.submit("bob", "msg_a",
                new SubmitFeedbackCommand("UP", List.of("SOLVED"), null, false)));
        assertEquals(ErrorCode.FORBIDDEN, error.errorCode());
    }

    /** 历史无主会话不可反馈，统一按不存在处理。 */
    @Test
    void legacySessionFeedbackRejected() {
        when(messages.findById("msg_a")).thenReturn(Optional.of(message("msg_a", "assistant", "回答")));
        when(sessions.findById("s_1")).thenReturn(Optional.of(session(null)));

        BusinessException error = assertThrows(BusinessException.class, () -> service.submit("alice", "msg_a",
                new SubmitFeedbackCommand("UP", List.of("SOLVED"), null, false)));
        assertEquals(ErrorCode.NOT_FOUND, error.errorCode());
    }

    /** 质量汇总使用真实反馈与消息数计算比率。 */
    @Test
    void statsComputesRatesFromRows() {
        Instant now = Instant.now();
        MessageFeedback up = new MessageFeedback("fb_1", "msg_1", "s_1", "a_1", "alice",
                FeedbackRating.UP, List.of(FeedbackReason.SOLVED), null, false, "q1", "a1", now, now);
        MessageFeedback down = new MessageFeedback("fb_2", "msg_2", "s_1", "a_1", "bob",
                FeedbackRating.DOWN, List.of(FeedbackReason.INCOMPLETE), null, true, "q2", "a2", now, now);
        when(feedbacks.find(isNull(), isNull(), any())).thenReturn(List.of(up, down));
        when(messages.countAssistant(any(), isNull())).thenReturn(10L);
        when(messages.countRefusal(any(), isNull())).thenReturn(2L);

        FeedbackStats stats = service.stats(null, 7);

        assertEquals(2, stats.total());
        assertEquals(1, stats.up());
        assertEquals(1, stats.down());
        assertEquals(0.2, stats.feedbackRate());
        assertEquals(0.5, stats.positiveRate());
        assertEquals(0.2, stats.refusalRate());
        assertEquals(1, stats.reasons().size());
    }

    private SessionMessage message(String id, String role, String content) {
        return new SessionMessage(id, "s_1", role, content, 0, null, 0, Instant.now());
    }

    private ChatSession session(String owner) {
        return new ChatSession("s_1", "a_1", "知识问答助手", "success", 1, 0, 10, 120,
                "10:00:00", "问题", List.of(), owner);
    }
}
