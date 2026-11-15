package com.agentplatform.modules.observe.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import com.agentplatform.modules.observe.domain.model.Alert;

@TableName(value = "alerts", autoResultMap = true)
public class AlertEntity {
    @TableId(type = IdType.INPUT) private String id;
    private String severity;
    private String title;
    @TableField(value = "rule_label") private String ruleLabel;
    private String target;
    @TableField(value = "time_label") private String timeLabel;
    private String ago;
    private String status;
    private String metric;
    private String detail;
    public static AlertEntity from(Alert a) {
        AlertEntity e = new AlertEntity(); e.id=a.id(); e.severity=a.severity(); e.title=a.title(); e.ruleLabel=a.rule();
        e.target=a.target(); e.timeLabel=a.time(); e.ago=a.ago(); e.status=a.status(); e.metric=a.metric(); e.detail=a.detail(); return e;
    }
    public Alert toDomain() { return new Alert(id, severity, title, ruleLabel, target, timeLabel, ago, status, metric, detail); }
}
