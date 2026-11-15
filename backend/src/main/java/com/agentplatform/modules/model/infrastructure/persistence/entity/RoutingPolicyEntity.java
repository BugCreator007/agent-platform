package com.agentplatform.modules.model.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.agentplatform.common.persistence.StringListTypeHandler;

import com.agentplatform.modules.model.domain.model.RoutingPolicy;

import java.util.List;

/**
 * 路由策略表（routing_policy）持久化实体（infrastructure 层）。
 *
 * <p>全局只保存一条记录，固定主键为 “default”；回退链经
 * {@code StringListTypeHandler} 存为字符串列。与领域值对象
 * {@link RoutingPolicy} 互转。</p>
 */
@TableName(value = "routing_policy", autoResultMap = true)
public class RoutingPolicyEntity {
    /** 固定主键，单例记录使用 “default”。 */
    @TableId(type = IdType.INPUT) private String id;
    /** 路由策略：failover/cost/latency/quality。 */
    private String strategy;
    /** 主模型 ID。 */
    @TableField(value = "primary_model") private String primaryModel;
    /** 回退模型 ID 列表。 */
    @TableField(typeHandler = StringListTypeHandler.class) private List<String> fallback;
    /** 区域。 */
    private String region;

    /**
     * 领域值对象转持久化实体（主键固定为 default）。
     *
     * @param p 路由策略
     * @return 对应实体
     */
    public static RoutingPolicyEntity from(RoutingPolicy p) {
        RoutingPolicyEntity e = new RoutingPolicyEntity();
        e.id = "default"; e.strategy = p.strategy(); e.primaryModel = p.primary(); e.fallback = p.fallback(); e.region = p.region();
        return e;
    }

    /**
     * 持久化实体转领域值对象；回退链为空时用空列表兜底。
     *
     * @return 路由策略
     */
    public RoutingPolicy toDomain() { return new RoutingPolicy(strategy, primaryModel, fallback == null ? List.of() : fallback, region); }
}
