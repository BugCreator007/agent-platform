package com.agentplatform.modules.system.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import com.agentplatform.modules.system.domain.model.Member;

@TableName(value = "members", autoResultMap = true)
public class MemberEntity {
    @TableId(type = IdType.INPUT) private String name;
    private String email;
    private String role;
    private int agents;
    @TableField(value = "last_seen") private String lastSeen;
    public static MemberEntity from(Member m) { MemberEntity e = new MemberEntity(); e.name=m.name(); e.email=m.email(); e.role=m.role(); e.agents=m.agents(); e.lastSeen=m.last(); return e; }
    public Member toDomain() { return new Member(name, email, role, agents, lastSeen); }
    public String getName() { return name; }
}
