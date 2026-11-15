package com.agentplatform.modules.system.infrastructure.persistence.repository;

import com.agentplatform.modules.system.infrastructure.persistence.mapper.UserMapper;

import com.agentplatform.modules.system.domain.model.UserAccount;
import com.agentplatform.modules.system.infrastructure.persistence.entity.UserEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@link com.agentplatform.modules.system.domain.repository.UserRepository} 的 MyBatis-Plus 适配器。
 *
 * <p>位于基础设施持久化层，封装 {@link UserMapper} 并完成领域模型与实体的双向转换；
 * 账号不存在时返回空 {@link Optional}，由认证用例统一处理。</p>
 */
@Repository
public class UserRepository implements com.agentplatform.modules.system.domain.repository.UserRepository {
    private final UserMapper mapper;

    /**
     * @param mapper 用户 Mapper
     */
    public UserRepository(UserMapper mapper) { this.mapper = mapper; }

    /** {@inheritDoc} */
    @Override public Optional<UserAccount> findByUsername(String username) { return Optional.ofNullable(mapper.findByUsername(username)).map(UserEntity::toDomain); }
    /** {@inheritDoc} */
    @Override public List<UserAccount> findAll() { return mapper.selectList(null).stream().map(UserEntity::toDomain).toList(); }
    /** {@inheritDoc} */
    @Override public void save(UserAccount user) { mapper.insertOrUpdate(UserEntity.from(user)); }
}
