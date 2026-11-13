package com.agentplatform.modules.system.infrastructure.persistence.entity;

import com.agentplatform.common.persistence.StringListConverter;
import com.agentplatform.modules.system.domain.model.UserAccount;
import jakarta.persistence.*;

import java.util.List;

@Entity
@Table(name = "users")
public class UserEntity {
    @Id private String id;
    @Column(nullable = false, unique = true) private String username;
    @Column(name = "password_hash", nullable = false) private String passwordHash;
    @Column(name = "display_name") private String displayName;
    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "TEXT") private List<String> roles;
    private boolean enabled;
    public static UserEntity from(UserAccount u) {
        UserEntity e = new UserEntity(); e.id=u.id(); e.username=u.username(); e.passwordHash=u.passwordHash();
        e.displayName=u.displayName(); e.roles=u.roles(); e.enabled=u.enabled(); return e;
    }
    public UserAccount toDomain() { return new UserAccount(id, username, passwordHash, displayName, roles == null ? List.of() : roles, enabled); }
}
