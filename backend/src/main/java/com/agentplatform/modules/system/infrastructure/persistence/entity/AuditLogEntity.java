package com.agentplatform.modules.system.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import com.agentplatform.modules.system.domain.model.AuditLog;

/**
 * 审计日志持久化实体。
 *
 * <p>位于基础设施持久化层，映射 {@code audit_logs} 表；主键由数据库自增生成，
 * 提供与领域模型的双向转换。审计只追加、不修改。</p>
 */
@TableName(value = "audit_logs", autoResultMap = true)
public class AuditLogEntity {
    /** 自增主键 */
    @TableId(type = IdType.AUTO) private Long id;
    /** 操作人 */
    private String actor;
    /** 操作描述 */
    private String action;
    /** 操作对象（对应列 object_name） */
    @TableField(value = "object_name") private String objectName;
    /** 发生时间（对应列 occurred_at，格式化文本） */
    @TableField(value = "occurred_at") private String occurredAt;
    /** 客户端 IP */
    private String ip;
    /** 预留扩展详情（当前种子/写入路径未填充） */
    private String detail;

    /**
     * 领域模型转实体。
     *
     * @param a 领域模型
     * @return 持久化实体
     */
    public static AuditLogEntity from(AuditLog a) { AuditLogEntity e = new AuditLogEntity(); e.actor=a.who(); e.action=a.action(); e.objectName=a.object(); e.occurredAt=a.time(); e.ip=a.ip(); return e; }

    /**
     * 实体转领域模型。
     *
     * @return 领域模型
     */
    public AuditLog toDomain() { return new AuditLog(actor, action, objectName, occurredAt, ip); }
}
