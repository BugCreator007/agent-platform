package com.agentplatform.modules.system.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import com.agentplatform.modules.system.domain.model.Member;

/**
 * 成员持久化实体。
 *
 * <p>位于基础设施持久化层，映射 {@code members} 表；以成员姓名为主键（INPUT 策略），
 * 提供与领域模型的双向转换。</p>
 */
@TableName(value = "members", autoResultMap = true)
public class MemberEntity {
    /** 主键：成员姓名 */
    @TableId(type = IdType.INPUT) private String name;
    /** 邮箱 */
    private String email;
    /** 角色 */
    private String role;
    /** 名下智能体数量 */
    private int agents;
    /** 最近活跃时间（对应列 last_seen） */
    @TableField(value = "last_seen") private String lastSeen;

    /**
     * 领域模型转实体。
     *
     * @param m 领域模型
     * @return 持久化实体
     */
    public static MemberEntity from(Member m) { MemberEntity e = new MemberEntity(); e.name=m.name(); e.email=m.email(); e.role=m.role(); e.agents=m.agents(); e.lastSeen=m.last(); return e; }

    /**
     * 实体转领域模型。
     *
     * @return 领域模型
     */
    public Member toDomain() { return new Member(name, email, role, agents, lastSeen); }

    /**
     * 获取成员姓名（主键）。
     *
     * @return 成员姓名
     */
    public String getName() { return name; }
}
