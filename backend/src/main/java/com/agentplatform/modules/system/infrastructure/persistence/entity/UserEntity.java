package com.agentplatform.modules.system.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.agentplatform.common.persistence.StringListTypeHandler;

import com.agentplatform.modules.system.domain.model.UserAccount;

import java.util.List;

/**
 * 用户账号持久化实体。
 *
 * <p>位于基础设施持久化层，映射 {@code users} 表；{@code roles} 通过
 * {@link StringListTypeHandler} 序列化为单个 TEXT 列（U+001F 分隔），
 * 并为 {@code autoResultMap} 生效所需的 TypeHandler 注册。
 * 密码列只存 BCrypt 哈希。</p>
 */
@TableName(value = "users", autoResultMap = true)
public class UserEntity {
    /** 主键：UUID */
    @TableId(type = IdType.INPUT) private String id;
    /** 登录名 */
    private String username;
    /** BCrypt 密码哈希 */
    @TableField(value = "password_hash") private String passwordHash;
    /** 展示名 */
    @TableField(value = "display_name") private String displayName;
    /** 角色列表，经 TypeHandler 序列化存储 */
    @TableField(typeHandler = StringListTypeHandler.class) private List<String> roles;
    /** 是否启用 */
    private boolean enabled;

    /**
     * 领域模型转实体。
     *
     * @param u 领域模型
     * @return 持久化实体
     */
    public static UserEntity from(UserAccount u) {
        UserEntity e = new UserEntity(); e.id=u.id(); e.username=u.username(); e.passwordHash=u.passwordHash();
        e.displayName=u.displayName(); e.roles=u.roles(); e.enabled=u.enabled(); return e;
    }

    /**
     * 实体转领域模型。
     *
     * <p>历史数据中 roles 列可能为空，转换为空列表避免调用方 NPE。</p>
     *
     * @return 领域模型
     */
    public UserAccount toDomain() { return new UserAccount(id, username, passwordHash, displayName, roles == null ? List.of() : roles, enabled); }
}
