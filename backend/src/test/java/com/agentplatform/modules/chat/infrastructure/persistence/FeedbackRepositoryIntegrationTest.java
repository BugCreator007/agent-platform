package com.agentplatform.modules.chat.infrastructure.persistence;

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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 反馈持久化集成测试：验证从点踩改为点赞时旧原因与备注被清空。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FeedbackRepositoryIntegrationTest {
    @Autowired private FeedbackRepository feedbacks;
    @Autowired private SessionRepository sessions;
    @Autowired private SessionMessageRepository messages;

    /** DOWN → UP 后 reasons/comment 必须为空，而不是保留旧值。 */
    @Test
    void updateClearsReasonsAndComment() {
        sessions.save(new ChatSession("s_fb_test", "a1", "助手", "success", 1, 0, 0, 0,
                "10:00:00", "问题", List.of(), "alice"));
        messages.save(new SessionMessage("msg_fb_test", "s_fb_test", "assistant", "回答",
                0, "m", 0, Instant.now()));
        Instant now = Instant.now();
        feedbacks.save(new MessageFeedback("fb_test", "msg_fb_test", "s_fb_test", "a1", "alice",
                FeedbackRating.DOWN, List.of(FeedbackReason.INCOMPLETE), "少了入口", false,
                "问题", "回答", now, now, FeedbackHandleStatus.OPEN, "助手"));

        feedbacks.save(new MessageFeedback("fb_test", "msg_fb_test", "s_fb_test", "a1", "alice",
                FeedbackRating.UP, List.of(), null, false,
                "问题", "回答", now, Instant.now(), FeedbackHandleStatus.OPEN, "助手"));

        MessageFeedback loaded = feedbacks.findByMessageAndUser("msg_fb_test", "alice").orElseThrow();
        assertEquals(FeedbackRating.UP, loaded.rating());
        assertTrue(loaded.safeReasons().isEmpty());
        assertNull(loaded.comment());
    }
}
