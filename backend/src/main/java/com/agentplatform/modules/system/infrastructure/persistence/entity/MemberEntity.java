package com.agentplatform.modules.system.infrastructure.persistence.entity;

import com.agentplatform.modules.system.domain.model.Member;
import jakarta.persistence.*;

@Entity
@Table(name = "members")
public class MemberEntity {
    @Id private String name;
    private String email;
    private String role;
    private int agents;
    @Column(name = "last_seen") private String lastSeen;
    public static MemberEntity from(Member m) { MemberEntity e = new MemberEntity(); e.name=m.name(); e.email=m.email(); e.role=m.role(); e.agents=m.agents(); e.lastSeen=m.last(); return e; }
    public Member toDomain() { return new Member(name, email, role, agents, lastSeen); }
    public String getName() { return name; }
}
