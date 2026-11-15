package com.agentplatform.modules.observe.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import com.agentplatform.modules.observe.domain.model.AlertRule;

/**
 * 告警规则表（alert_rules）持久化实体（infrastructure 层）。
 *
 * <p>与领域值对象 {@link AlertRule} 通过 {@link #from(AlertRule)}、{@link #toDomain()} 互转；
 * 下划线列名通过 {@code @TableField} 显式映射。</p>
 */
@TableName(value = "alert_rules", autoResultMap = true)
public class AlertRuleEntity {
    /** 规则 ID（业务主键，非自增）。 */
    @TableId(type = IdType.INPUT) private String id;
    /** 规则名称。 */
    private String name;
    /** 指标名。 */
    private String metric;
    /** 告警级别。 */
    private String level;
    /** 触发阈值。 */
    @TableField(value = "threshold_value") private double thresholdValue;
    /** 统计窗口展示值。 */
    @TableField(value = "window_label") private String windowLabel;
    /** 通知渠道展示值。 */
    private String channel;
    /** 是否启用。 */
    private boolean enabled;

    /**
     * 领域值对象转持久化实体。
     *
     * @param r 告警规则
     * @return 对应实体
     */
    public static AlertRuleEntity from(AlertRule r) { AlertRuleEntity e = new AlertRuleEntity();
        e.id=r.id(); e.name=r.name(); e.metric=r.metric(); e.level=r.level(); e.thresholdValue=r.thresholdValue();
        e.windowLabel=r.windowLabel(); e.channel=r.channel(); e.enabled=r.enabled(); return e; }

    /**
     * 持久化实体转领域值对象。
     *
     * @return 告警规则
     */
    public AlertRule toDomain() { return new AlertRule(id, name, metric, level, thresholdValue, windowLabel, channel, enabled); }
}
