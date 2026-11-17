package com.agentplatform.modules.chat.interfaces.rest;

import com.agentplatform.modules.chat.domain.model.ChatSession;
import com.agentplatform.modules.chat.domain.model.SessionMessage;
import com.agentplatform.modules.chat.domain.repository.SessionMessageRepository;
import com.agentplatform.modules.chat.domain.repository.SessionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 会话与管理端接口安全集成测试：401/403/404、本人可见与历史无主策略。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SessionSecurityTest {
    @Autowired private MockMvc mvc;
    @Autowired private SessionRepository sessions;
    @Autowired private SessionMessageRepository messages;

    /** 准备三个会话：alice、bob 与历史无主。 */
    @BeforeEach
    void setUp() {
        sessions.save(new ChatSession("s_sec_alice", "a1", "助手", "success", 1, 0, 0, 10,
                "10:00:00", "alice 问题", List.of(), "alice"));
        sessions.save(new ChatSession("s_sec_bob", "a1", "助手", "success", 1, 0, 0, 10,
                "10:00:00", "bob 问题", List.of(), "bob"));
        sessions.save(new ChatSession("s_sec_legacy", "a1", "助手", "success", 1, 0, 0, 10,
                "10:00:00", "历史问题", List.of(), null));
        messages.save(new SessionMessage("msg_sec_alice", "s_sec_alice", "assistant", "alice 回答",
                0, "m", 0, Instant.now()));
        messages.save(new SessionMessage("msg_sec_bob", "s_sec_bob", "assistant", "bob 回答",
                0, "m", 0, Instant.now()));
    }

    /** 清理测试数据，避免影响其他用例。 */
    @AfterEach
    void tearDown() {
        messages.deleteBySession("s_sec_alice");
        messages.deleteBySession("s_sec_bob");
        messages.deleteBySession("s_sec_legacy");
        sessions.deleteById("s_sec_alice");
        sessions.deleteById("s_sec_bob");
        sessions.deleteById("s_sec_legacy");
    }

    /** 未登录访问会话列表返回 401。 */
    @Test
    void unauthenticatedRejected() throws Exception {
        mvc.perform(get("/api/v1/sessions")).andExpect(status().isUnauthorized());
    }

    /** VIEWER 的会话列表只包含本人，不包含他人与历史无主会话。 */
    @Test
    void viewerSeesOnlyOwnSessions() throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/sessions")
                        .with(jwt().jwt(j -> j.subject("alice"))
                                .authorities(new SimpleGrantedAuthority("ROLE_VIEWER"))))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("s_sec_alice"));
        assertFalse(body.contains("s_sec_bob"));
        assertFalse(body.contains("s_sec_legacy"));
    }

    /** VIEWER 读他人消息返回 404，读本人消息返回 200。 */
    @Test
    void viewerCannotReadOthersMessages() throws Exception {
        mvc.perform(get("/api/v1/sessions/s_sec_bob/messages")
                        .with(jwt().jwt(j -> j.subject("alice"))
                                .authorities(new SimpleGrantedAuthority("ROLE_VIEWER"))))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/sessions/s_sec_bob/messages")
                        .with(jwt().jwt(j -> j.subject("bob"))
                                .authorities(new SimpleGrantedAuthority("ROLE_VIEWER"))))
                .andExpect(status().isOk());
    }

    /** 历史无主会话普通用户不可读，开发者可读。 */
    @Test
    void legacySessionOnlyVisibleToDeveloper() throws Exception {
        mvc.perform(get("/api/v1/sessions/s_sec_legacy/messages")
                        .with(jwt().jwt(j -> j.subject("alice"))
                                .authorities(new SimpleGrantedAuthority("ROLE_VIEWER"))))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/sessions/s_sec_legacy/messages")
                        .with(jwt().jwt(j -> j.subject("dev"))
                                .authorities(new SimpleGrantedAuthority("ROLE_DEVELOPER"))))
                .andExpect(status().isOk());
    }

    /** 管理端运行指标仅管理员/开发者可读。 */
    @Test
    void statsRequiresAdminRole() throws Exception {
        mvc.perform(get("/api/v1/sessions/stats")
                        .with(jwt().jwt(j -> j.subject("alice"))
                                .authorities(new SimpleGrantedAuthority("ROLE_VIEWER"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/sessions/stats")
                        .with(jwt().jwt(j -> j.subject("dev"))
                                .authorities(new SimpleGrantedAuthority("ROLE_DEVELOPER"))))
                .andExpect(status().isOk());
    }

    /** 客户端事件的消息必须属于当前会话，防止采纳事件挂到他人消息。 */
    @Test
    void eventMessageMustBelongToSession() throws Exception {
        String own = """
                {"name":"answer_copied","eventId":"evt_sec_own","sessionId":"s_sec_alice",
                 "messageId":"msg_sec_alice","agentId":"a1","properties":{"length":3}}
                """;
        mvc.perform(post("/api/v1/user/events")
                        .with(jwt().jwt(j -> j.subject("alice"))
                                .authorities(new SimpleGrantedAuthority("ROLE_VIEWER")))
                        .contentType(MediaType.APPLICATION_JSON).content(own))
                .andExpect(status().isOk());

        String mismatch = """
                {"name":"answer_copied","eventId":"evt_sec_bad","sessionId":"s_sec_alice",
                 "messageId":"msg_sec_bob","agentId":"a1","properties":{"length":3}}
                """;
        mvc.perform(post("/api/v1/user/events")
                        .with(jwt().jwt(j -> j.subject("alice"))
                                .authorities(new SimpleGrantedAuthority("ROLE_VIEWER")))
                        .contentType(MediaType.APPLICATION_JSON).content(mismatch))
                .andExpect(status().isNotFound());
    }

    /** VIEWER 无权读取反馈，开发者可读；反馈接口返回分页对象。 */
    @Test
    void feedbackPermissions() throws Exception {
        mvc.perform(get("/api/v1/feedback")
                        .with(jwt().jwt(j -> j.subject("alice"))
                                .authorities(new SimpleGrantedAuthority("ROLE_VIEWER"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/feedback")
                        .with(jwt().jwt(j -> j.subject("dev"))
                                .authorities(new SimpleGrantedAuthority("ROLE_DEVELOPER"))))
                .andExpect(status().isOk());
    }
}
