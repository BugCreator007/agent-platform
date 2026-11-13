package com.agentplatform.modules.observe.application.service;

import com.agentplatform.modules.observe.domain.model.CostSummary;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CostApplicationService {
    private double budget = 20000;

    public CostSummary summary() {
        List<CostSummary.CostItem> models = List.of(
                new CostSummary.CostItem("Claude Sonnet 5.5", 6820, 54.6),
                new CostSummary.CostItem("Claude Opus 5.5", 3240, 26.0),
                new CostSummary.CostItem("Claude Haiku 5.5", 1120, 9.0),
                new CostSummary.CostItem("Qwen3-Max", 610, 4.9),
                new CostSummary.CostItem("DeepSeek-V4", 420, 3.4),
                new CostSummary.CostItem("其他", 270, 2.1)
        );
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

    public CostSummary updateBudget(double budget) {
        this.budget = Math.max(1, budget);
        return summary();
    }
}
