package com.agentplatform.modules.chat.infrastructure.persistence;

import com.agentplatform.modules.chat.domain.repository.SessionRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 会话演示数据初始化器（infrastructure 层）。
 *
 * <p>仅在 {@code app.demo.seed} 未显式关闭（默认开启）且会话表为空时写入种子会话，
 * 用于本地演示与首次启动体验；生产环境可通过配置关闭。</p>
 */
@Component
@ConditionalOnProperty(name = "app.demo.seed", matchIfMissing = true)
public class ChatDataSeeder implements ApplicationRunner {
    private final SessionRepository repository;
    public ChatDataSeeder(SessionRepository repository) { this.repository = repository; }

    /**
     * 应用启动后执行：会话表为空时批量写入演示会话。
     *
     * @param args 启动参数（未使用）
     */
    @Override public void run(ApplicationArguments args) {
        if (repository.findAll().isEmpty()) new SessionSeedData().findAll().forEach(repository::save);
    }
}
