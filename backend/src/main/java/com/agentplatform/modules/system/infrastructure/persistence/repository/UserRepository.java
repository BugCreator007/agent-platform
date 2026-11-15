package com.agentplatform.modules.system.infrastructure.persistence.repository;

import com.agentplatform.modules.system.infrastructure.persistence.mapper.UserMapper;

import com.agentplatform.modules.system.domain.model.UserAccount;
import com.agentplatform.modules.system.infrastructure.persistence.entity.UserEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class UserRepository implements com.agentplatform.modules.system.domain.repository.UserRepository {
    private final UserMapper mapper;
    public UserRepository(UserMapper mapper) { this.mapper = mapper; }
    @Override public Optional<UserAccount> findByUsername(String username) { return Optional.ofNullable(mapper.findByUsername(username)).map(UserEntity::toDomain); }
    @Override public List<UserAccount> findAll() { return mapper.selectList(null).stream().map(UserEntity::toDomain).toList(); }
    @Override public void save(UserAccount user) { mapper.insertOrUpdate(UserEntity.from(user)); }
}
