package com.agentplatform;

import com.agentplatform.modules.agent.domain.repository.AgentRepository;
import com.agentplatform.modules.system.domain.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spring 上下文集成测试（H2 MySQL 兼容模式 + Flyway 真实迁移）。
 *
 * <p>该测试会启动完整应用上下文并执行演示种子数据，因此同时覆盖 MyBatis-Plus
 * Mapper、TypeHandler、仓储适配器与模块装配是否正确。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class ApplicationContextTests {

    /** 用户仓储，用于验证角色列表 TypeHandler 读写。 */
    @Autowired
    private UserRepository users;

    /** 智能体仓储，用于验证工具/知识库列表 TypeHandler 读写。 */
    @Autowired
    private AgentRepository agents;

    /**
     * 冒烟测试：应用上下文能够完整启动。
     */
    @Test
    void contextLoads() {
    }

    /**
     * 校验 MyBatis-Plus 的 TypeHandler 字段（列表/JSON）能正确写库并读回。
     *
     * <p>种子数据在上下文启动时通过 Mapper 写入，这里再从仓储读取，
     * 能同时发现「写入未使用 TypeHandler」和「查询缺少 autoResultMap」两类问题。</p>
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
