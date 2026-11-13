package com.agentplatform.modules.model.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Provider {
    private final String id;
    private final String name;
    private String status;
    private final String endpoint;
    private final String protocol;
    private long models;
    private long calls;
    private double share;
    private String latency;
    private double success;
    private double quota;
    private double used;
    private String key;
    private String apiKey;
    private String region;
    private String route;

    public Provider(String id, String name, String status, String endpoint, long models, long calls,
                    double share, String latency, double success, double quota, double used,
                    String key, String region, String route) {
        this(id, name, status, endpoint, models, calls, share, latency, success, quota, used,
                key, null, "openai", region, route);
    }

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
        this.key = this.apiKey == null ? (key == null || key.isBlank() ? "未配置" : key) : maskKey(this.apiKey);
        this.region = region;
        this.route = route;
    }

    public void markTested(String latency) {
        this.latency = latency;
        this.status = "healthy";
    }

    public void markFailed(String latency) {
        this.latency = latency;
        this.status = "degraded";
    }

    public void updateKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            throw new IllegalArgumentException("API Key 不能为空");
        }
        this.apiKey = rawKey.trim();
        this.key = maskKey(this.apiKey);
    }

    public void updateQuota(double quota) { this.quota = Math.max(0, quota); }

    public void toggle() {
        this.status = "paused".equals(status) ? "healthy" : "paused";
        this.route = "paused".equals(status) ? "已停用" : "备用";
    }

    public String id() { return id; }
    public String name() { return name; }
    public String status() { return status; }
    public String endpoint() { return endpoint; }
    public String protocol() { return protocol; }
    public long models() { return models; }
    public long calls() { return calls; }
    public double share() { return share; }
    public String latency() { return latency; }
    public double success() { return success; }
    public double quota() { return quota; }
    public double used() { return used; }
    public String key() { return key; }
    @JsonIgnore public String apiKey() { return apiKey; }
    @JsonProperty("keyConfigured")
    public boolean keyConfigured() { return apiKey != null && !apiKey.isBlank(); }
    public String region() { return region; }
    public String route() { return route; }

    private static String maskKey(String value) {
        if (value == null || value.isBlank()) return "未配置";
        if (value.length() <= 8) return "••••••••";
        return value.substring(0, Math.min(6, value.length() - 4)) + "••••••••" + value.substring(value.length() - 4);
    }
}
