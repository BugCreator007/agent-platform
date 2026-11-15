package com.agentplatform.modules.system.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import com.agentplatform.modules.system.infrastructure.persistence.entity.UserEntity;

public interface UserMapper extends BaseMapper<UserEntity> {
    UserEntity findByUsername(@Param("username") String username);
}
