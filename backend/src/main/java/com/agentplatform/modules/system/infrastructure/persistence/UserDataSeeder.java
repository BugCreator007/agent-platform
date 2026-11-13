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

@Component
@ConditionalOnProperty(name = "app.demo.seed", matchIfMissing = true)
public class UserDataSeeder implements ApplicationRunner {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final String adminPassword;

    public UserDataSeeder(UserRepository users, PasswordEncoder encoder,
                          @Value("${app.security.bootstrap-admin-password:admin123}") String adminPassword) {
        this.users = users;
        this.encoder = encoder;
        this.adminPassword = adminPassword;
    }

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
