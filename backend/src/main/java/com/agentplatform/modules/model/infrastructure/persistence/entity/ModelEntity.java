package com.agentplatform.modules.model.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.agentplatform.common.persistence.StringListTypeHandler;

import com.agentplatform.modules.model.domain.model.ModelInfo;

import java.util.List;

/**
 * 模型表（models）持久化实体（infrastructure 层）。
 *
 * <p>仅用于 MyBatis-Plus 读写，与领域对象 {@link ModelInfo} 通过 {@link #from(ModelInfo)}
 * 和 {@link #toDomain()} 互转；能力标签列表经 {@code StringListTypeHandler} 存为字符串列。</p>
 */
@TableName(value = "models", autoResultMap = true)
public class ModelEntity {
    /** 模型 ID（业务主键，非自增）。 */
    @TableId(type = IdType.INPUT) private String id;
    /** 所属厂商 ID。 */
    @TableField(value = "provider_id") private String providerId;
    /** 模型展示名。 */
    private String name;
    /** 上下文窗口展示值。 */
    @TableField(value = "context_window") private String context;
    /** 输入价格（每百万 token）。 */
    @TableField(value = "in_price") private double inPrice;
    /** 输出价格（每百万 token）。 */
    @TableField(value = "out_price") private double outPrice;
    /** 能力标签，按 JSON/逗号列表存储。 */
    @TableField(typeHandler = StringListTypeHandler.class) private List<String> capabilities;
    /** 累计调用次数。 */
    private long calls;
    /** 平均延迟展示值。 */
    private String latency;
    /** 状态：active/degraded/paused。 */
    private String status;
    /** 路由权重。 */
    @TableField(value = "route_weight") private int routeWeight;
    /** 是否默认模型。 */
    @TableField(value = "is_default") private boolean isDefault;
    /** 上游真实模型名。 */
    @TableField(value = "upstream_model") private String upstreamModel;

    /**
     * 领域对象转持久化实体。
     *
     * @param m 模型领域对象
     * @return 对应实体
     */
    public static ModelEntity from(ModelInfo m) {
        ModelEntity e = new ModelEntity();
        e.id = m.id(); e.providerId = m.providerId(); e.name = m.name(); e.context = m.context();
        e.inPrice = m.inPrice(); e.outPrice = m.outPrice(); e.capabilities = m.capabilities();
        e.calls = m.calls(); e.latency = m.latency(); e.status = m.status(); e.routeWeight = m.routeWeight();
        e.isDefault = m.isDefault(); e.upstreamModel = m.upstreamModel();
        return e;
    }

    /**
     * 持久化实体转领域对象；能力标签为空时用空列表兜底。
     *
     * @return 模型领域对象
     */
    public ModelInfo toDomain() {
        return new ModelInfo(id, providerId, name, context, inPrice, outPrice,
                capabilities == null ? List.of() : capabilities, calls, latency, status, routeWeight,
                isDefault, upstreamModel);
    }
}
