package com.agentplatform.modules.model.domain.model;

import java.util.List;

/**
 * 模型领域对象（domain 层）。
 *
 * <p>描述平台目录中的一个可调用模型：归属厂商、上游真实模型名、上下文窗口、
 * 价格、能力标签、路由权重与状态。对象本身不感知持久化，由基础设施层映射。</p>
 *
 * <p>约束：{@code id} 为业务主键；{@code upstreamModel} 为空时回退为 id；
 * 价格单位为“每百万 token”；{@code routeWeight} 越大越优（quality 策略使用）。</p>
 */
public class ModelInfo {
    /** 模型 ID（业务主键）。 */
    private final String id;
    /** 所属厂商 ID。 */
    private final String providerId;
    /** 模型展示名。 */
    private final String name;
    /** 上下文窗口展示值，如 “128K”。 */
    private final String context;
    /** 输入价格（每百万 token，元）。 */
    private final double inPrice;
    /** 输出价格（每百万 token，元）。 */
    private final double outPrice;
    /** 能力标签，如“推理”“视觉”“代码”。 */
    private final List<String> capabilities;
    /** 累计调用次数（统计展示用）。 */
    private final long calls;
    /** 平均延迟展示值，如 “620 ms”。 */
    private final String latency;
    /** 模型状态：active/degraded/paused。 */
    private final String status;
    /** 路由权重，越大越优先。 */
    private final int routeWeight;
    /** 是否为默认模型（全局唯一）。 */
    private boolean isDefault;
    /** 发送给上游的真实模型名。 */
    private final String upstreamModel;

    /**
     * 兼容构造器：未指定上游模型名时使用模型 ID。
     *
     * @param id           模型 ID
     * @param providerId   厂商 ID
     * @param name         展示名
     * @param context      上下文窗口
     * @param inPrice      输入价格
     * @param outPrice     输出价格
     * @param capabilities 能力标签
     * @param calls        调用次数
     * @param latency      延迟展示值
     * @param status       状态
     * @param routeWeight  路由权重
     * @param isDefault    是否默认模型
     */
    public ModelInfo(String id, String providerId, String name, String context, double inPrice,
                     double outPrice, List<String> capabilities, long calls, String latency,
                     String status, int routeWeight, boolean isDefault) {
        this(id, providerId, name, context, inPrice, outPrice, capabilities, calls, latency,
                status, routeWeight, isDefault, id);
    }

    /**
     * 完整构造器。
     *
     * @param id            模型 ID
     * @param providerId    厂商 ID
     * @param name          展示名
     * @param context       上下文窗口
     * @param inPrice       输入价格
     * @param outPrice      输出价格
     * @param capabilities  能力标签
     * @param calls         调用次数
     * @param latency       延迟展示值
     * @param status        状态
     * @param routeWeight   路由权重
     * @param isDefault     是否默认模型
     * @param upstreamModel 上游真实模型名，为空时回退为 id
     */
    public ModelInfo(String id, String providerId, String name, String context, double inPrice,
                     double outPrice, List<String> capabilities, long calls, String latency,
                     String status, int routeWeight, boolean isDefault, String upstreamModel) {
        this.id = id;
        this.providerId = providerId;
        this.name = name;
        this.context = context;
        this.inPrice = inPrice;
        this.outPrice = outPrice;
        this.capabilities = List.copyOf(capabilities);
        this.calls = calls;
        this.latency = latency;
        this.status = status;
        this.routeWeight = routeWeight;
        this.isDefault = isDefault;
        // 上游模型名缺失时退回模型 ID，保证路由始终有可发送的模型名
        this.upstreamModel = upstreamModel == null || upstreamModel.isBlank() ? id : upstreamModel;
    }

    /**
     * 设置默认标记（由应用服务维护全局唯一性）。
     *
     * @param value 是否默认模型
     */
    public void setDefault(boolean value) { this.isDefault = value; }

    /**
     * 模型 ID。
     *
     * @return 模型 ID
     */
    public String id() { return id; }

    /**
     * 所属厂商 ID。
     *
     * @return 厂商 ID
     */
    public String providerId() { return providerId; }

    /**
     * 模型展示名。
     *
     * @return 展示名
     */
    public String name() { return name; }

    /**
     * 上下文窗口展示值。
     *
     * @return 如 “128K”
     */
    public String context() { return context; }

    /**
     * 输入价格。
     *
     * @return 每百万 token 价格
     */
    public double inPrice() { return inPrice; }

    /**
     * 输出价格。
     *
     * @return 每百万 token 价格
     */
    public double outPrice() { return outPrice; }

    /**
     * 能力标签。
     *
     * @return 不可变的能力标签列表
     */
    public List<String> capabilities() { return capabilities; }

    /**
     * 累计调用次数。
     *
     * @return 调用次数
     */
    public long calls() { return calls; }

    /**
     * 平均延迟展示值。
     *
     * @return 如 “620 ms”
     */
    public String latency() { return latency; }

    /**
     * 模型状态。
     *
     * @return active/degraded/paused
     */
    public String status() { return status; }

    /**
     * 路由权重。
     *
     * @return 权重值
     */
    public int routeWeight() { return routeWeight; }

    /**
     * 是否为默认模型。
     *
     * @return true 表示默认模型
     */
    public boolean isDefault() { return isDefault; }

    /**
     * 上游真实模型名。
     *
     * @return 上游模型名
     */
    public String upstreamModel() { return upstreamModel; }
}
