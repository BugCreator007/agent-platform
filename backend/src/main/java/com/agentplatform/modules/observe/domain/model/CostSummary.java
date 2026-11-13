package com.agentplatform.modules.observe.domain.model;

import java.util.List;

public record CostSummary(double total, double budget, List<CostItem> models, List<DailyCost> daily) {
    public record CostItem(String name, double value, double share) {}
    public record DailyCost(String day, double value) {}
}
