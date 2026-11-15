package com.agentplatform;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Spring Modulith 模块边界测试。
 *
 * <p>验证各限界上下文之间只通过 application/query 或 application/service 的
 * {@code @NamedInterface} 交互，防止 domain/infrastructure 被跨模块直接依赖。</p>
 */
class ModularityTests {

    /**
     * 校验模块结构合法；违反模块边界时测试失败并给出具体依赖路径。
     */
    @Test
    void verifiesModuleStructure() {
        ApplicationModules.of(AgentPlatformApplication.class).verify();
    }
}
