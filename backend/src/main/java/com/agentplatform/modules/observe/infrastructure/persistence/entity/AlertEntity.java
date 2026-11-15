package com.agentplatform.modules.observe.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import com.agentplatform.modules.observe.domain.model.Alert;

/**
 * 告警表（alerts）持久化实体（infrastructure 层）。
 *
 * <p>与领域对象 {@link Alert} 通过 {@link #from(Alert)}、{@link #toDomain()} 互转；
 * 由于领域字段 {@code rule}/{@code time} 与数据库列名差异，使用 {@code @TableField} 显式映射。</p>
 */
@TableName(value = "alerts", autoResultMap = true)
public class AlertEntity {
    /** 告警 ID（业务主键，非自增）。 */
    @TableId(type = IdType.INPUT) private String id;
    /** 严重级别。 */
    private String severity;
    /** 告警标题。 */
    private String title;
    /** 关联规则/条件（对应领域字段 rule）。 */
    @TableField(value = "rule_label") private String ruleLabel;
    /** 影响对象。 */
    private String target;
    /** 触发时间展示值（对应领域字段 time）。 */
    @TableField(value = "time_label") private String timeLabel;
    /** 相对时间展示值。 */
    private String ago;
    /** 状态：active/ack/resolved。 */
    private String status;
    /** 指标展示值。 */
    private String metric;
    /** 详细说明。 */
    private String detail;

    /**
     * 领域对象转持久化实体。
     *
     * @param a 告警领域对象
     * @return 对应实体
     */
    public static AlertEntity from(Alert a) {
        AlertEntity e = new AlertEntity(); e.id=a.id(); e.severity=a.severity(); e.title=a.title(); e.ruleLabel=a.rule();
        e.target=a.target(); e.timeLabel=a.time(); e.ago=a.ago(); e.status=a.status(); e.metric=a.metric(); e.detail=a.detail(); return e;
    }

    /**
     * 持久化实体转领域对象。
     *
     * @return 告警领域对象
     */
    public Alert toDomain() { return new Alert(id, severity, title, ruleLabel, target, timeLabel, ago, status, metric, detail); }
}
