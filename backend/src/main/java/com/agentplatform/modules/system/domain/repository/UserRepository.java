package com.agentplatform.modules.system.domain.repository;

import com.agentplatform.modules.system.domain.model.UserAccount;

import java.util.List;
import java.util.Optional;

/**
 * 用户账号仓储端口。
 *
 * <p>位于领域层，供认证用例读取/写入账号；由 infrastructure 层基于 MyBatis-Plus 实现。</p>
 */
public interface UserRepository {
    /**
     * 按登录名查找账号。
     *
     * @param username 登录名
     * @return 账号（可能为空）
     */
    Optional<UserAccount> findByUsername(String username);

    /**
     * 查询全部账号（用于判断是否需要初始化种子数据）。
     *
     * @return 账号列表
     */
    List<UserAccount> findAll();

    /**
     * 新增或更新账号。
     *
     * @param user 账号
     */
    void save(UserAccount user);
}
