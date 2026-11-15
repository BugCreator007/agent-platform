package com.agentplatform;

import com.agentplatform.modules.agent.domain.repository.AgentRepository;
import com.agentplatform.modules.system.domain.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class ApplicationContextTests {

    @Autowired
    private UserRepository users;

    @Autowired
    private AgentRepository agents;

    @Test
    void contextLoads() {
    }

    /**
     * 校验 MyBatis-Plus 的 TypeHandler 字段（列表/JSON）能正确写库并读回。
     */
    @Test
    void typeHandlerColumnsRoundTrip() {
        var admin = users.findByUsername("admin").orElseThrow();
        assertTrue(admin.roles().contains("PLATFORM_ADMIN"), "users.roles 应通过 TypeHandler 读回");

        var agent = agents.findById("a1").orElseThrow();
        assertFalse(agent.tools().isEmpty(), "agents.tools 应通过 TypeHandler 读回");
        assertFalse(agent.kb().isEmpty(), "agents.kb 应通过 TypeHandler 读回");
    }
}
