package com.agentplatform.modules.system.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.agentplatform.common.persistence.StringListTypeHandler;

import com.agentplatform.modules.system.domain.model.UserAccount;

import java.util.List;

@TableName(value = "users", autoResultMap = true)
public class UserEntity {
    @TableId(type = IdType.INPUT) private String id;
    private String username;
    @TableField(value = "password_hash") private String passwordHash;
    @TableField(value = "display_name") private String displayName;
    @TableField(typeHandler = StringListTypeHandler.class) private List<String> roles;
    private boolean enabled;
    public static UserEntity from(UserAccount u) {
        UserEntity e = new UserEntity(); e.id=u.id(); e.username=u.username(); e.passwordHash=u.passwordHash();
        e.displayName=u.displayName(); e.roles=u.roles(); e.enabled=u.enabled(); return e;
    }
    public UserAccount toDomain() { return new UserAccount(id, username, passwordHash, displayName, roles == null ? List.of() : roles, enabled); }
}
