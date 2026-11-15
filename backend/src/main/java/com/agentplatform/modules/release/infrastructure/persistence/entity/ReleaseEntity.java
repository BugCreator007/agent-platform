package com.agentplatform.modules.release.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import com.agentplatform.modules.release.domain.model.Release;

/**
 * 发布记录数据库实体（infrastructure 层），映射 {@code releases} 表。
 * <p>只负责与数据库互转；状态机规则在领域聚合 {@link Release} 中。</p>
 */
@TableName(value = "releases", autoResultMap = true)
public class ReleaseEntity {
    /** 主键，由应用层生成（r 前缀），不使用数据库自增。 */
    @TableId(type = IdType.INPUT) private String id;
    /** 关联智能体 id，列名 {@code agent_id}。 */
    @TableField(value = "agent_id") private String agentId;
    /** 智能体名称快照，列名 {@code agent_name}。 */
    @TableField(value = "agent_name") private String agentName;
    /** 待发布版本号。 */
    private String version;
    /** 当前发布阶段（draft/approve/canary/live/rollback）。 */
    private String stage;
    /** 流量百分比（0-100）。 */
    private int traffic;
    /** 评测得分，可为 null，列名 {@code eval_score}。 */
    @TableField(value = "eval_score") private Double evalScore;
    /** 操作人。 */
    private String owner;
    /** 时间展示文本；领域名称为 time，列名 {@code time_label}。 */
    @TableField(value = "time_label") private String timeLabel;
    /** 发布说明。 */
    private String note;

    /**
     * 领域聚合转数据库实体。
     *
     * @param r 发布聚合
     * @return 持久化实体
     */
    public static ReleaseEntity from(Release r) {
        ReleaseEntity e = new ReleaseEntity();
        e.id = r.id(); e.agentId = r.agentId(); e.agentName = r.agentName(); e.version = r.version();
        e.stage = r.stage(); e.traffic = r.traffic(); e.evalScore = r.evalScore(); e.owner = r.owner();
        e.timeLabel = r.time(); e.note = r.note();
        return e;
    }

    /**
     * 数据库实体转领域聚合。
     *
     * @return 发布聚合
     */
    public Release toDomain() {
        return new Release(id, agentId, agentName, version, stage, traffic, evalScore, owner, timeLabel, note);
    }
}
