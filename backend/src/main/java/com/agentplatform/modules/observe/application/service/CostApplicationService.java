package com.agentplatform.modules.observe.application.service;

import com.agentplatform.modules.observe.domain.model.CostSummary;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 成本应用服务（应用层）。
 *
 * <p>提供成本汇总与月度预算维护。当前汇总数据为演示用静态数据：
 * 预算保存在本服务内存中，进程重启后恢复默认值；成本明细不随真实调用变化。
 * 概览页的真实成本估算由 {@code OverviewApplicationService} 另行计算。</p>
 */
@Service
public class CostApplicationService {
    /** 月度预算（内存态，仅当前进程有效）。 */
    private double budget = 20000;

    /**
     * 获取成本汇总（总额、预算、按模型分布、按天趋势）。
     *
     * @return 成本汇总
     */
    public CostSummary summary() {
        // 演示数据：按模型成本与占比
        List<CostSummary.CostItem> models = List.of(
                new CostSummary.CostItem("Claude Sonnet 5.5", 6820, 54.6),
                new CostSummary.CostItem("Claude Opus 5.5", 3240, 26.0),
                new CostSummary.CostItem("Claude Haiku 5.5", 1120, 9.0),
                new CostSummary.CostItem("Qwen3-Max", 610, 4.9),
                new CostSummary.CostItem("DeepSeek-V4", 420, 3.4),
                new CostSummary.CostItem("其他", 270, 2.1)
        );
        // 演示数据：近 7 天每日成本
        List<CostSummary.DailyCost> daily = List.of(
                new CostSummary.DailyCost("11/07", 1620),
                new CostSummary.DailyCost("11/08", 1710),
                new CostSummary.DailyCost("11/09", 1680),
                new CostSummary.DailyCost("11/10", 1780),
                new CostSummary.DailyCost("11/11", 1760),
                new CostSummary.DailyCost("11/12", 1830),
                new CostSummary.DailyCost("11/13", 2100)
        );
        return new CostSummary(12480, budget, models, daily);
    }

    /**
     * 更新月度预算，最小值为 1，避免预算为 0 造成使用率除零。
     *
     * @param budget 新预算
     * @return 更新后的成本汇总
     */
    public CostSummary updateBudget(double budget) {
        this.budget = Math.max(1, budget);
        return summary();
    }
}
