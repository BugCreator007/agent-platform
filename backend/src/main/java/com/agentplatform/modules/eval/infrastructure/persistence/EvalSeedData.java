package com.agentplatform.modules.eval.infrastructure.persistence;

import com.agentplatform.modules.eval.domain.model.EvalRun;
import com.agentplatform.modules.eval.domain.model.EvalSuite;

import java.util.ArrayList;
import java.util.List;

/**
 * 评测演示种子数据构造器（infrastructure 层）。
 * <p>仅在首次启动、eval_suites 表为空时由 {@link EvalDataSeeder} 读取并落库，
 * 不是运行时存储。运行记录中的智能体名称与 release 种子数据中的名称保持一致，
 * 以便发布门禁能按名称匹配到评测记录。</p>
 */
public class EvalSeedData {
    /** 评测集种子列表。 */
    private final List<EvalSuite> suites = new ArrayList<>();
    /** 评测运行记录种子列表。 */
    private final List<EvalRun> runs = new ArrayList<>();

    /**
     * 构造 4 个评测集与 4 条运行记录，覆盖成功与失败状态。
     */
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

    /**
     * @return 评测集种子快照
     */
    public List<EvalSuite> suites() { return List.copyOf(suites); }

    /**
     * @return 运行记录种子快照
     */
    public List<EvalRun> runs() { return List.copyOf(runs); }

    /**
     * 追加运行记录到列表头部，保证页面按“最新在前”展示（仅内存构造期使用）。
     *
     * @param run 运行记录
     */
    public void addRun(EvalRun run) { runs.add(0, run); }
}
