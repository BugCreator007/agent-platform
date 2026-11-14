package com.agentplatform.modules.workflow.infrastructure.persistence;

import com.agentplatform.modules.workflow.domain.model.Workflow;
import com.agentplatform.modules.workflow.domain.model.WorkflowNode;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class WorkflowSeedData {
    private final Map<String, Workflow> store = new ConcurrentHashMap<>();

    public WorkflowSeedData() {
        store.put("wf-ticket", new Workflow("wf-ticket", "售后工单处理流程", "published",
                "会话意图触发", 1842, 98.2, "顾先路", "v12", "今天 09:42",
                List.of(new WorkflowNode("start", "START", "工单进入", "会话 · 售后意图"),
                        new WorkflowNode("kb", "KNOWLEDGE", "检索售后政策", "kb-refund · Top 4"),
                        new WorkflowNode("judge", "MODEL", "判断是否可退", "Claude Sonnet 5.5"),
                        new WorkflowNode("branch", "CONDITION", "是否可退？", "can_refund == true"),
                        new WorkflowNode("refund", "TOOL", "发起退款", "refund.apply"),
                        new WorkflowNode("approval", "APPROVAL", "大额退款审批", "金额 > ¥500"),
                        new WorkflowNode("fallback", "MODEL", "生成拒绝说明", "Claude Haiku 5.5"),
                        new WorkflowNode("end", "END", "结束", "回写会话"))));
        store.put("wf-refund", new Workflow("wf-refund", "退款审核编排", "published", "工单事件触发",
                964, 97.4, "李婧", "v8", "今天 11:32", List.of(
                new WorkflowNode("start", "START", "工单进入", "工单事件"),
                new WorkflowNode("order", "TOOL", "查询订单", "order.query"),
                new WorkflowNode("risk", "TOOL", "查询风险分", "risk.score"),
                new WorkflowNode("branch", "CONDITION", "是否高风险？", "risk_score < 80"),
                new WorkflowNode("approval", "APPROVAL", "人工复核", "风控值班人"),
                new WorkflowNode("end", "END", "回写结论", "审核结果"))));
        store.put("wf-invoice", new Workflow("wf-invoice", "发票重开流程", "published", "API 调用触发",
                412, 99.1, "顾先路", "v5", "昨天 17:20", List.of(
                new WorkflowNode("start", "START", "重开请求", "API 调用"),
                new WorkflowNode("kb", "KNOWLEDGE", "检索开票规则", "kb-invoice"),
                new WorkflowNode("model", "MODEL", "生成重开方案", "Claude Sonnet 5.5"),
                new WorkflowNode("tool", "TOOL", "红冲并重开", "invoice.issue"),
                new WorkflowNode("end", "END", "返回结果", "API 响应"))));
        store.put("wf-report", new Workflow("wf-report", "商家周报生成", "draft", "定时任务 · 每天 09:00",
                0, 0, "周越", "v2", "今天 09:42", List.of(
                new WorkflowNode("start", "START", "定时触发", "每天 09:00"),
                new WorkflowNode("bi", "TOOL", "查询经营数据", "bi.query"),
                new WorkflowNode("model", "MODEL", "生成周报", "Claude Sonnet 5.5"),
                new WorkflowNode("notify", "TOOL", "发送通知", "notify.send"),
                new WorkflowNode("end", "END", "结束", "发送完成"))));
        store.put("wf-risk", new Workflow("wf-risk", "高风险交易处置", "paused", "告警触发",
                208, 95.6, "陈昊", "v6", "3 天前", List.of(
                new WorkflowNode("start", "START", "告警触发", "风险告警"),
                new WorkflowNode("risk", "TOOL", "复核风险分", "risk.score"),
                new WorkflowNode("branch", "CONDITION", "是否冻结？", "risk_score < 60"),
                new WorkflowNode("approval", "APPROVAL", "风控复核", "陈昊"),
                new WorkflowNode("notify", "TOOL", "发送通知", "notify.send"),
                new WorkflowNode("end", "END", "结束", "处置完成"))));
        store.put("wf-qa", new Workflow("wf-qa", "质检评分流程", "published", "会话结束事件",
                736, 98.0, "李婧", "v9", "今天 07:30", List.of(
                new WorkflowNode("start", "START", "会话结束", "会话事件"),
                new WorkflowNode("kb", "KNOWLEDGE", "检索质检规则", "kb-ticket"),
                new WorkflowNode("model", "MODEL", "五维评分", "Claude Sonnet 5.5"),
                new WorkflowNode("end", "END", "写回结果", "质检工单"))));
        store.put("wf-weather", new Workflow("wf-weather", "上海天气查询", "published", "手动触发",
                0, 0, "顾先路", "v1", "刚刚", List.of(
                new WorkflowNode("start", "START", "接收地址", "用户提交地址",
                        Map.of("next", List.of("route"))),
                new WorkflowNode("route", "CONDITION", "地址是否包含上海", "address.contains('上海')",
                        Map.of("expression", "address.contains('上海')", "trueNext", "weather", "falseNext", "model")),
                new WorkflowNode("weather", "TOOL", "查询上海天气", "weather.query",
                        Map.of("tool", "weather.query", "next", List.of("join"))),
                new WorkflowNode("model", "MODEL", "模型兜底回答", "非上海地址",
                        Map.of("systemPrompt", "你是天气助手。用户地址不在上海，请说明当前仅支持上海实时天气查询，并给出可执行的下一步。",
                                "next", List.of("join"))),
                new WorkflowNode("join", "JOIN", "汇聚结果", "等待任一路径",
                        Map.of("next", List.of("end"))),
                new WorkflowNode("end", "END", "结束", "返回结果",
                        Map.of()))));
        store.put("wf-ad", new Workflow("wf-ad", "投放素材生成", "draft", "手动触发",
                0, 0, "周越", "v1", "2 天前", List.of(
                new WorkflowNode("start", "START", "人工触发", "运营提交 brief"),
                new WorkflowNode("model", "MODEL", "生成三组文案", "Claude Sonnet 5.5"),
                new WorkflowNode("branch", "CONDITION", "合规检查", "sensitive_hits == 0"),
                new WorkflowNode("approval", "APPROVAL", "人工确认", "投放负责人"),
                new WorkflowNode("end", "END", "结束", "输出素材"))));
    }

    public List<Workflow> findAll() { return List.copyOf(store.values()); }
    public Optional<Workflow> findById(String id) { return Optional.ofNullable(store.get(id)); }
    public void save(Workflow workflow) { store.put(workflow.id(), workflow); }
}
