package com.agentplatform.modules.release.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import com.agentplatform.modules.release.domain.model.Release;

@TableName(value = "releases", autoResultMap = true)
public class ReleaseEntity {
    @TableId(type = IdType.INPUT) private String id;
    @TableField(value = "agent_id") private String agentId;
    @TableField(value = "agent_name") private String agentName;
    private String version;
    private String stage;
    private int traffic;
    @TableField(value = "eval_score") private Double evalScore;
    private String owner;
    @TableField(value = "time_label") private String timeLabel;
    private String note;

    public static ReleaseEntity from(Release r) {
        ReleaseEntity e = new ReleaseEntity();
        e.id = r.id(); e.agentId = r.agentId(); e.agentName = r.agentName(); e.version = r.version();
        e.stage = r.stage(); e.traffic = r.traffic(); e.evalScore = r.evalScore(); e.owner = r.owner();
        e.timeLabel = r.time(); e.note = r.note();
        return e;
    }
    public Release toDomain() {
        return new Release(id, agentId, agentName, version, stage, traffic, evalScore, owner, timeLabel, note);
    }
}
