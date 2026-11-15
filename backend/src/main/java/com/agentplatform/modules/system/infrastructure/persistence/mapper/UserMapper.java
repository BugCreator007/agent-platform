package com.agentplatform.modules.system.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import com.agentplatform.modules.system.infrastructure.persistence.entity.UserEntity;

/**
 * 用户账号 MyBatis-Plus Mapper。
 *
 * <p>位于基础设施持久化层，除标准 CRUD 外提供按登录名查询；
 * SQL 位于 {@code resources/mapper/UserMapper.xml}。</p>
 */
public interface UserMapper extends BaseMapper<UserEntity> {
    /**
     * 按登录名查询用户。
     *
     * @param username 登录名
     * @return 用户实体，不存在返回 {@code null}
     */
    UserEntity findByUsername(@Param("username") String username);
}
