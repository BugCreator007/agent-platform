package com.agentplatform.modules.eval.infrastructure.persistence;

import com.agentplatform.modules.eval.domain.model.EvalRun;
import com.agentplatform.modules.eval.domain.model.EvalSuite;

import java.util.ArrayList;
import java.util.List;

public class EvalSeedData {
    private final List<EvalSuite> suites = new ArrayList<>();
    private final List<EvalRun> runs = new ArrayList<>();

    public EvalSeedData() {
        suites.add(new EvalSuite("ev-cs", "客服问答 v3", "覆盖退款、物流、发票三类意图，共 320 条", 94.2, 320, "12 分钟前"));
        suites.add(new EvalSuite("ev-tool", "工具选择", "校验工具选择与参数填充的准确性", 98.1, 240, "1 小时前"));
        suites.add(new EvalSuite("ev-refund", "退款政策", "政策条款引用与边界条件判断", 91.6, 180, "昨天"));
        suites.add(new EvalSuite("ev-multi", "多轮追问", "多轮上下文保持与指代消解", 88.4, 150, "昨天"));
        runs.add(new EvalRun("run_8f21", "客服问答 v3", "售后工单助手", "v25", "302/320", 94.2, "6m 12s", "14:20", "success"));
        runs.add(new EvalRun("run_8f1c", "工具选择", "退款审核 Copilot", "v12", "236/240", 98.1, "4m 48s", "13:36", "success"));
        runs.add(new EvalRun("run_8ef0", "退款政策", "退款审核 Copilot", "v12", "165/180", 91.6, "3m 05s", "昨天 18:12", "success"));
        runs.add(new EvalRun("run_8e90", "多轮追问", "售后工单助手", "v24", "133/150", 88.4, "2m 51s", "昨天 16:40", "failed"));
    }

    public List<EvalSuite> suites() { return List.copyOf(suites); }
    public List<EvalRun> runs() { return List.copyOf(runs); }
    public void addRun(EvalRun run) { runs.add(0, run); }
}
