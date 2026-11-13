package com.agentplatform.modules.system.domain.repository;

import com.agentplatform.modules.system.domain.model.UserAccount;

import java.util.List;
import java.util.Optional;

public interface UserRepository {
    Optional<UserAccount> findByUsername(String username);
    List<UserAccount> findAll();
    void save(UserAccount user);
}
