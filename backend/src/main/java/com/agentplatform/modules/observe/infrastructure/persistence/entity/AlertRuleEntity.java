package com.agentplatform.modules.observe.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import com.agentplatform.modules.observe.domain.model.AlertRule;

@TableName(value = "alert_rules", autoResultMap = true)
public class AlertRuleEntity {
    @TableId(type = IdType.INPUT) private String id;
    private String name;
    private String metric;
    private String level;
    @TableField(value = "threshold_value") private double thresholdValue;
    @TableField(value = "window_label") private String windowLabel;
    private String channel;
    private boolean enabled;
    public static AlertRuleEntity from(AlertRule r) { AlertRuleEntity e = new AlertRuleEntity();
        e.id=r.id(); e.name=r.name(); e.metric=r.metric(); e.level=r.level(); e.thresholdValue=r.thresholdValue();
        e.windowLabel=r.windowLabel(); e.channel=r.channel(); e.enabled=r.enabled(); return e; }
    public AlertRule toDomain() { return new AlertRule(id, name, metric, level, thresholdValue, windowLabel, channel, enabled); }
}
