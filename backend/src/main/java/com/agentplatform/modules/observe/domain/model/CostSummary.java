package com.agentplatform.modules.observe.domain.model;

import java.util.List;

/**
 * 成本汇总（domain 层值对象）。
 *
 * @param total  当期总成本（元）
 * @param budget 月度预算（元）
 * @param models 按模型维度的成本明细
 * @param daily  按天维度的成本趋势
 */
public record CostSummary(double total, double budget, List<CostItem> models, List<DailyCost> daily) {
    /**
     * 单个模型的成本明细。
     *
     * @param name  模型展示名
     * @param value 成本金额（元）
     * @param share 成本占比（百分比）
     */
    public record CostItem(String name, double value, double share) {}

    /**
     * 单日成本。
     *
     * @param day   日期标签（MM/dd）
     * @param value 当日成本（元）
     */
    public record DailyCost(String day, double value) {}
}
