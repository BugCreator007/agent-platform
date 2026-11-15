package com.agentplatform.modules.system.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import com.agentplatform.modules.system.domain.model.ApiKey;

/**
 * API Key 持久化实体。
 *
 * <p>位于基础设施持久化层，映射 {@code api_keys} 表；主键使用业务密钥值（INPUT 策略），
 * 并提供与领域模型的双向转换。表结构仅由 Flyway 迁移维护。</p>
 */
@TableName(value = "api_keys", autoResultMap = true)
public class ApiKeyEntity {
    /** 主键：密钥值（创建时指定） */
    @TableId(type = IdType.INPUT) private String id;
    /** 密钥名称 */
    private String name;
    /** 密钥值（对应列 key_value） */
    @TableField(value = "key_value") private String keyValue;
    /** 授权范围 */
    private String scope;
    /** 创建日期 */
    private String created;
    /** 最近使用时间（对应列 last_used） */
    @TableField(value = "last_used") private String lastUsed;
    /** 创建人（对应列 created_by） */
    @TableField(value = "created_by") private String createdBy;

    /**
     * 领域模型转实体。
     *
     * @param k 领域模型
     * @return 持久化实体
     */
    public static ApiKeyEntity from(ApiKey k) { ApiKeyEntity e = new ApiKeyEntity(); e.id=k.key(); e.name=k.name(); e.keyValue=k.key(); e.scope=k.scope(); e.created=k.created(); e.lastUsed=k.last(); e.createdBy=k.by(); return e; }

    /**
     * 实体转领域模型。
     *
     * @return 领域模型
     */
    public ApiKey toDomain() { return new ApiKey(name, keyValue, scope, created, lastUsed, createdBy); }
}
