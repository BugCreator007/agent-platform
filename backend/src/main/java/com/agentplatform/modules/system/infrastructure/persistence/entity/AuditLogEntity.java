package com.agentplatform.modules.system.infrastructure.persistence.entity;

import com.agentplatform.modules.system.domain.model.AuditLog;
import jakarta.persistence.*;

@Entity
@Table(name = "audit_logs")
public class AuditLogEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private String actor;
    private String action;
    @Column(name = "object_name") private String objectName;
    @Column(name = "occurred_at") private String occurredAt;
    private String ip;
    @Column(columnDefinition = "TEXT") private String detail;
    public static AuditLogEntity from(AuditLog a) { AuditLogEntity e = new AuditLogEntity(); e.actor=a.who(); e.action=a.action(); e.objectName=a.object(); e.occurredAt=a.time(); e.ip=a.ip(); return e; }
    public AuditLog toDomain() { return new AuditLog(actor, action, objectName, occurredAt, ip); }
}
