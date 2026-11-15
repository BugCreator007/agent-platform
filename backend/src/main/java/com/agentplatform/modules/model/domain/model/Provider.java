package com.agentplatform.modules.model.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 模型厂商领域对象（domain 层）。
 *
 * <p>保存厂商连接信息、状态、额度与密钥。密钥安全约束：
 * 真实密钥只保存在 {@code apiKey} 字段，序列化时用 {@code @JsonIgnore} 屏蔽；
 * {@code key} 字段仅存放脱敏展示值。持久化前由基础设施层使用 {@code SecretCipher} 加密。</p>
 */
public class Provider {
    /** 厂商 ID（业务主键）。 */
    private final String id;
    /** 厂商名称。 */
    private final String name;
    /** 厂商状态：healthy/degraded/paused。 */
    private String status;
    /** API 基地址。 */
    private final String endpoint;
    /** 调用协议：openai（OpenAI 兼容）或 anthropic。 */
    private final String protocol;
    /** 厂商下模型数量（统计展示用）。 */
    private long models;
    /** 累计调用次数（统计展示用）。 */
    private long calls;
    /** 调用占比（统计展示用）。 */
    private double share;
    /** 平均延迟展示值，如 “620 ms”。 */
    private String latency;
    /** 成功率（百分比）。 */
    private double success;
    /** 额度上限。 */
    private double quota;
    /** 已使用额度。 */
    private double used;
    /** 脱敏后的密钥展示值，未配置时为 “未配置”。 */
    private String key;
    /** 真实 API Key（敏感，不参与序列化）。 */
    private String apiKey;
    /** 区域，如 “华东 1”。 */
    private String region;
    /** 路由角色展示值，如 “主路由”“备用”“已停用”。 */
    private String route;

    /**
     * 兼容构造器：未指定真实密钥与协议时按 OpenAI 兼容协议处理。
     *
     * @param id       厂商 ID
     * @param name     名称
     * @param status   状态
     * @param endpoint 基地址
     * @param models   模型数量
     * @param calls    调用次数
     * @param share    调用占比
     * @param latency  延迟展示值
     * @param success  成功率
     * @param quota    额度
     * @param used     已用额度
     * @param key      密钥展示值（或已有脱敏值）
     * @param region   区域
     * @param route    路由角色
     */
    public Provider(String id, String name, String status, String endpoint, long models, long calls,
                    double share, String latency, double success, double quota, double used,
                    String key, String region, String route) {
        this(id, name, status, endpoint, models, calls, share, latency, success, quota, used,
                key, null, "openai", region, route);
    }

    /**
     * 完整构造器。
     *
     * @param id       厂商 ID
     * @param name     名称
     * @param status   状态
     * @param endpoint 基地址
     * @param models   模型数量
     * @param calls    调用次数
     * @param share    调用占比
     * @param latency  延迟展示值
     * @param success  成功率
     * @param quota    额度
     * @param used     已用额度
     * @param key      已有的脱敏展示值（仅在 apiKey 为空时使用）
     * @param apiKey   真实 API Key（明文，持久化时加密）
     * @param protocol 调用协议，为空时默认 openai
     * @param region   区域
     * @param route    路由角色
     */
    public Provider(String id, String name, String status, String endpoint, long models, long calls,
                    double share, String latency, double success, double quota, double used,
                    String key, String apiKey, String protocol, String region, String route) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.endpoint = endpoint;
        this.protocol = protocol == null || protocol.isBlank() ? "openai" : protocol;
        this.models = models;
        this.calls = calls;
        this.share = share;
        this.latency = latency;
        this.success = success;
        this.quota = quota;
        this.used = used;
        this.apiKey = apiKey == null || apiKey.isBlank() ? null : apiKey.trim();
        // 有真实密钥时由密钥生成脱敏展示值；否则沿用传入的展示值（老数据兼容）
        this.key = this.apiKey == null ? (key == null || key.isBlank() ? "未配置" : key) : maskKey(this.apiKey);
        this.region = region;
        this.route = route;
    }

    /**
     * 记录一次成功的连通性探测。
     *
     * @param latency 本次探测延迟展示值
     */
    public void markTested(String latency) {
        this.latency = latency;
        this.status = "healthy";
    }

    /**
     * 记录一次失败的连通性探测。
     *
     * @param latency 本次探测延迟展示值（未发出请求时为 “—”）
     */
    public void markFailed(String latency) {
        this.latency = latency;
        this.status = "degraded";
    }

    /**
     * 更新 API Key，同时刷新脱敏展示值。
     *
     * @param rawKey 新的明文密钥
     * @throws IllegalArgumentException 密钥为空时抛出
     */
    public void updateKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            throw new IllegalArgumentException("API Key 不能为空");
        }
        this.apiKey = rawKey.trim();
        this.key = maskKey(this.apiKey);
    }

    /**
     * 更新额度，负数按 0 处理。
     *
     * @param quota 新额度
     */
    public void updateQuota(double quota) { this.quota = Math.max(0, quota); }

    /**
     * 在启用（healthy）与停用（paused）之间切换，并同步路由角色展示值。
     */
    public void toggle() {
        this.status = "paused".equals(status) ? "healthy" : "paused";
        this.route = "paused".equals(status) ? "已停用" : "备用";
    }

    /**
     * 厂商 ID。
     *
     * @return 厂商 ID
     */
    public String id() { return id; }

    /**
     * 厂商名称。
     *
     * @return 名称
     */
    public String name() { return name; }

    /**
     * 厂商状态。
     *
     * @return healthy/degraded/paused
     */
    public String status() { return status; }

    /**
     * API 基地址。
     *
     * @return 基地址
     */
    public String endpoint() { return endpoint; }

    /**
     * 调用协议。
     *
     * @return openai 或 anthropic
     */
    public String protocol() { return protocol; }

    /**
     * 厂商下模型数量。
     *
     * @return 模型数量
     */
    public long models() { return models; }

    /**
     * 累计调用次数。
     *
     * @return 调用次数
     */
    public long calls() { return calls; }

    /**
     * 调用占比。
     *
     * @return 百分比
     */
    public double share() { return share; }

    /**
     * 平均延迟展示值。
     *
     * @return 如 “620 ms”
     */
    public String latency() { return latency; }

    /**
     * 成功率。
     *
     * @return 百分比
     */
    public double success() { return success; }

    /**
     * 额度上限。
     *
     * @return 额度
     */
    public double quota() { return quota; }

    /**
     * 已使用额度。
     *
     * @return 已用额度
     */
    public double used() { return used; }

    /**
     * 脱敏后的密钥展示值。
     *
     * @return 如 “sk-abc••••••••1234” 或 “未配置”
     */
    public String key() { return key; }

    /**
     * 真实 API Key（敏感，禁止序列化到接口响应）。
     *
     * @return 明文密钥，未配置时为 null
     */
    @JsonIgnore public String apiKey() { return apiKey; }

    /**
     * 是否已配置密钥，供前端展示状态。
     *
     * @return 已配置返回 true
     */
    @JsonProperty("keyConfigured")
    public boolean keyConfigured() { return apiKey != null && !apiKey.isBlank(); }

    /**
     * 区域。
     *
     * @return 区域名
     */
    public String region() { return region; }

    /**
     * 路由角色展示值。
     *
     * @return 如 “主路由”“备用”“已停用”
     */
    public String route() { return route; }

    /**
     * 生成脱敏密钥：保留前 6 位与后 4 位，中间以圆点替代；过短时全部遮蔽。
     *
     * @param value 明文密钥
     * @return 脱敏展示值
     */
    private static String maskKey(String value) {
        if (value == null || value.isBlank()) return "未配置";
        if (value.length() <= 8) return "••••••••";
        return value.substring(0, Math.min(6, value.length() - 4)) + "••••••••" + value.substring(value.length() - 4);
    }
}
