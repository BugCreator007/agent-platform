package com.agentplatform.modules.chat.application.service;

import com.agentplatform.modules.chat.application.query.FeedbackPage;
import com.agentplatform.modules.chat.domain.model.ChatSession;
import com.agentplatform.modules.chat.domain.model.FeedbackHandleStatus;
import com.agentplatform.modules.chat.domain.model.FeedbackRating;
import com.agentplatform.modules.chat.domain.model.FeedbackReason;
import com.agentplatform.modules.chat.domain.model.MessageFeedback;
import com.agentplatform.modules.chat.domain.model.SessionMessage;
import com.agentplatform.modules.chat.domain.repository.FeedbackRepository;
import com.agentplatform.modules.chat.domain.repository.SessionMessageRepository;
import com.agentplatform.modules.chat.domain.repository.SessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 反馈查询集成测试：验证 SQL 分页、原因过滤与总数。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FeedbackQueryIntegrationTest {
    @Autowired private FeedbackApplicationService service;
    @Autowired private FeedbackRepository feedbacks;
    @Autowired private SessionRepository sessions;
    @Autowired private SessionMessageRepository messages;

    /** 原因过滤与分页由 SQL 完成，总数与列表一致。 */
    @Test
    void filtersByReasonWithPagination() {
        Instant now = Instant.now();
        sessions.save(new ChatSession("s_query_test", "a1", "助手", "success", 1, 0, 0, 0,
                "10:00:00", "问题", List.of(), "alice"));
        messages.save(new SessionMessage("msg_q1", "s_query_test", "assistant", "回答1",
                0, "m", 0, now.minusSeconds(30)));
        messages.save(new SessionMessage("msg_q2", "s_query_test", "assistant", "回答2",
                0, "m", 0, now.minusSeconds(20)));
        feedbacks.save(feedback("fb_q1", "msg_q1", "alice", FeedbackRating.DOWN,
                List.of(FeedbackReason.INCOMPLETE), now.minusSeconds(30)));
        feedbacks.save(feedback("fb_q2", "msg_q2", "alice", FeedbackRating.UP,
                List.of(FeedbackReason.SOLVED), now.minusSeconds(20)));
        feedbacks.save(feedback("fb_q3", "msg_q2", "bob", FeedbackRating.DOWN,
                List.of(FeedbackReason.OUTDATED), now.minusSeconds(10)));

        FeedbackPage downIncomplete = service.list(null, "DOWN", "INCOMPLETE",
                now.minusSeconds(60), now.plusSeconds(60), 1, 10);
        assertEquals(1, downIncomplete.total());
        assertEquals(1, downIncomplete.items().size());
        assertEquals("fb_q1", downIncomplete.items().get(0).id());

        FeedbackPage firstPage = service.list(null, null, null,
                now.minusSeconds(60), now.plusSeconds(60), 1, 2);
        assertEquals(3, firstPage.total());
        assertEquals(2, firstPage.items().size());
        assertTrue(firstPage.items().get(0).createdAt().isAfter(firstPage.items().get(1).createdAt()));
    }

    private MessageFeedback feedback(String id, String messageId, String userId, FeedbackRating rating,
                                     List<FeedbackReason> reasons, Instant createdAt) {
        return new MessageFeedback(id, messageId, "s_query_test", "a1", userId, rating, reasons,
                null, false, "问题", "回答", createdAt, createdAt,
                FeedbackHandleStatus.OPEN, "助手");
    }
}
