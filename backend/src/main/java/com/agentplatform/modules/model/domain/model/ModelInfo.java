package com.agentplatform.modules.model.domain.model;

import java.util.List;

public class ModelInfo {
    private final String id;
    private final String providerId;
    private final String name;
    private final String context;
    private final double inPrice;
    private final double outPrice;
    private final List<String> capabilities;
    private final long calls;
    private final String latency;
    private final String status;
    private final int routeWeight;
    private boolean isDefault;
    private final String upstreamModel;

    public ModelInfo(String id, String providerId, String name, String context, double inPrice,
                     double outPrice, List<String> capabilities, long calls, String latency,
                     String status, int routeWeight, boolean isDefault) {
        this(id, providerId, name, context, inPrice, outPrice, capabilities, calls, latency,
                status, routeWeight, isDefault, id);
    }

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
        this.upstreamModel = upstreamModel == null || upstreamModel.isBlank() ? id : upstreamModel;
    }

    public void setDefault(boolean value) { this.isDefault = value; }
    public String id() { return id; }
    public String providerId() { return providerId; }
    public String name() { return name; }
    public String context() { return context; }
    public double inPrice() { return inPrice; }
    public double outPrice() { return outPrice; }
    public List<String> capabilities() { return capabilities; }
    public long calls() { return calls; }
    public String latency() { return latency; }
    public String status() { return status; }
    public int routeWeight() { return routeWeight; }
    public boolean isDefault() { return isDefault; }
    public String upstreamModel() { return upstreamModel; }
}
