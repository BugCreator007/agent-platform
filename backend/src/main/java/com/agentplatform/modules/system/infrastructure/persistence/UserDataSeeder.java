package com.agentplatform.modules.system.infrastructure.persistence;

import com.agentplatform.modules.system.domain.model.UserAccount;
import com.agentplatform.modules.system.domain.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * 登录账号演示数据初始化器。
 *
 * <p>位于基础设施层，应用启动时执行一次：仅当用户表为空时写入
 * admin/developer/viewer 三个演示账号，密码一律经 BCrypt 编码后落库。
 * 管理员密码可由 {@code app.security.bootstrap-admin-password} 覆盖。
 * 由 {@code app.demo.seed=false} 关闭（生产环境应关闭）。</p>
 */
@Component
@ConditionalOnProperty(name = "app.demo.seed", matchIfMissing = true)
public class UserDataSeeder implements ApplicationRunner {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    /** 初始化管理员密码（BCrypt 前的明文，仅用于首启编码） */
    private final String adminPassword;

    /**
     * @param users         用户仓储端口
     * @param encoder       BCrypt 密码编码器
     * @param adminPassword 管理员初始密码
     */
    public UserDataSeeder(UserRepository users, PasswordEncoder encoder,
                          @Value("${app.security.bootstrap-admin-password:admin123}") String adminPassword) {
        this.users = users;
        this.encoder = encoder;
        this.adminPassword = adminPassword;
    }

    /**
     * 启动时写入演示账号（已有数据则跳过）。
     *
     * @param args 应用启动参数（未使用）
     */
    @Override
    public void run(ApplicationArguments args) {
        if (!users.findAll().isEmpty()) return;
        users.save(new UserAccount(UUID.randomUUID().toString(), "admin",
                encoder.encode(adminPassword), "顾先路", List.of("PLATFORM_ADMIN"), true));
        users.save(new UserAccount(UUID.randomUUID().toString(), "developer",
                encoder.encode("developer123"), "李婧", List.of("DEVELOPER"), true));
        users.save(new UserAccount(UUID.randomUUID().toString(), "viewer",
                encoder.encode("viewer123"), "王可", List.of("VIEWER"), true));
    }
}
