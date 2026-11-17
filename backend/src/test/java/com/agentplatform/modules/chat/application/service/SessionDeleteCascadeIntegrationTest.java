package com.agentplatform.modules.chat.application.service;

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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 删除会话级联测试：会话、消息与反馈在删除后均无残留。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SessionDeleteCascadeIntegrationTest {
    @Autowired private SessionApplicationService service;
    @Autowired private SessionRepository sessions;
    @Autowired private SessionMessageRepository messages;
    @Autowired private FeedbackRepository feedbacks;

    /** 删除会话后消息与反馈被级联清理。 */
    @Test
    void deleteCascadesMessagesAndFeedback() {
        Instant now = Instant.now();
        sessions.save(new ChatSession("s_del_test", "a1", "助手", "success", 1, 0, 0, 0,
                "10:00:00", "问题", List.of(), "alice"));
        messages.save(new SessionMessage("msg_del_test", "s_del_test", "assistant", "回答",
                0, "m", 0, now));
        feedbacks.save(new MessageFeedback("fb_del_test", "msg_del_test", "s_del_test", "a1",
                "alice", FeedbackRating.DOWN, List.of(FeedbackReason.INCOMPLETE), null, false,
                "问题", "回答", now, now, FeedbackHandleStatus.OPEN, "助手"));

        service.delete("s_del_test", "admin");

        assertTrue(sessions.findById("s_del_test").isEmpty());
        assertTrue(messages.findById("msg_del_test").isEmpty());
        assertTrue(feedbacks.findByMessageAndUser("msg_del_test", "alice").isEmpty());
    }
}
