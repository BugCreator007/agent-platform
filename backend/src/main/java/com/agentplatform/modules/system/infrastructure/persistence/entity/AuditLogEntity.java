package com.agentplatform.modules.system.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import com.agentplatform.modules.system.domain.model.AuditLog;

@TableName(value = "audit_logs", autoResultMap = true)
public class AuditLogEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private String actor;
    private String action;
    @TableField(value = "object_name") private String objectName;
    @TableField(value = "occurred_at") private String occurredAt;
    private String ip;
    private String detail;
    public static AuditLogEntity from(AuditLog a) { AuditLogEntity e = new AuditLogEntity(); e.actor=a.who(); e.action=a.action(); e.objectName=a.object(); e.occurredAt=a.time(); e.ip=a.ip(); return e; }
    public AuditLog toDomain() { return new AuditLog(actor, action, objectName, occurredAt, ip); }
}
