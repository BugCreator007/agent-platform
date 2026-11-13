package com.agentplatform.modules.system.infrastructure.persistence;

import com.agentplatform.modules.system.domain.model.UserAccount;
import com.agentplatform.modules.system.domain.repository.UserRepository;
import com.agentplatform.modules.system.infrastructure.persistence.entity.UserEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JpaUserRepository implements UserRepository {
    private final UserJpaRepository jpa;
    public JpaUserRepository(UserJpaRepository jpa) { this.jpa = jpa; }
    @Override public Optional<UserAccount> findByUsername(String username) { return jpa.findByUsername(username).map(UserEntity::toDomain); }
    @Override public List<UserAccount> findAll() { return jpa.findAll().stream().map(UserEntity::toDomain).toList(); }
    @Override public void save(UserAccount user) { jpa.save(UserEntity.from(user)); }
}
