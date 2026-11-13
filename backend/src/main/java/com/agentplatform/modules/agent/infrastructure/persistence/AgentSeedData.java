package com.agentplatform.modules.agent.infrastructure.persistence;

import com.agentplatform.modules.agent.domain.model.Agent;
import com.agentplatform.modules.agent.domain.model.AgentStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class AgentSeedData {
    private final Map<String, Agent> store = new ConcurrentHashMap<>();

    public AgentSeedData() {
        seed("a1", "svc-ticket", "售后工单助手", AgentStatus.RUNNING, "v25", "Claude Sonnet 5.5",
                "顾先路", "客服平台", "灰度 20%",
                "你是售后工单助手，负责处理退款、物流、发票相关咨询。回答前必须先调用 order.query 核实订单状态；涉及退款金额时调用 refund.apply 预校验。",
                List.of("order.query", "refund.apply", "kb.search"), List.of("kb-refund", "kb-ticket"),
                1842, 98.7, "1.10s", 2180, "今天 09:42");
        seed("a2", "refund-copilot", "退款审核 Copilot", AgentStatus.GRAYSCALE, "v12", "Claude Opus 5.5",
                "李婧", "交易平台", "灰度 5%",
                "你辅助审核退款工单。金额超过 500 元或风控分低于 60 时必须转人工复核。",
                List.of("order.query", "risk.score", "refund.review"), List.of("kb-refund-rules"),
                964, 97.2, "1.82s", 3420, "今天 08:10");
        seed("a3", "merchant-report", "商家经营周报", AgentStatus.RUNNING, "v8", "Claude Sonnet 5.5",
                "周越", "商家增长", "全量",
                "根据商家经营数据生成周报，突出环比变化与异常项。",
                List.of("bi.query", "notify.send"), List.of(), 412, 99.1, "0.92s", 860, "昨天 18:22");
        seed("a4", "invoice-bot", "发票助手", AgentStatus.RUNNING, "v15", "Claude Haiku 5.5",
                "顾先路", "财务中台", "全量",
                "处理发票开具、红冲与抬头校验问题，开票前核对纳税识别号。",
                List.of("invoice.issue", "order.query"), List.of("kb-invoice"), 1205, 99.4, "0.61s", 420, "今天 11:05");
        seed("a5", "risk-qa", "风控问答", AgentStatus.PAUSED, "v6", "Qwen3-Max",
                "陈昊", "风控", "已回滚",
                "回答风控规则相关问题，仅引用已审核的规则条目。",
                List.of("risk.score"), List.of("kb-risk"), 208, 95.8, "2.20s", 310, "3 天前");
        seed("a6", "qa-inspector", "客服质检", AgentStatus.RUNNING, "v19", "Claude Sonnet 5.5",
                "李婧", "客服平台", "全量",
                "对客服会话进行质检，按五个维度打分并给出证据片段。",
                List.of("kb.search", "notify.send"), List.of("kb-refund", "kb-ticket"), 736, 98.1, "1.40s", 970, "今天 07:30");
        seed("a7", "ad-creative", "投放素材生成", AgentStatus.DRAFT, "v3", "DeepSeek-V4",
                "周越", "广告平台", "草稿",
                "根据投放 brief 生成三组素材文案，控制在 40 字以内。",
                List.of("web.fetch"), List.of(), 0, 0, "—", 0, "今天 09:42");
        seed("a8", "recon-helper", "对账异常助手", AgentStatus.GRAYSCALE, "v9", "Claude Sonnet 5.5",
                "顾先路", "交易平台", "灰度 5%",
                "定位对账差异原因，按渠道、时间、金额维度给出排查路径。",
                List.of("bi.query", "order.query"), List.of("kb-risk"), 332, 97.9, "1.60s", 610, "1 天前");
    }

    private void seed(String id, String code, String name, AgentStatus status, String version,
                      String model, String owner, String team, String release, String prompt,
                      List<String> tools, List<String> kb, long sessions, double success,
                      String p95, double cost, String updated) {
        Agent agent = Agent.restore(id, code, name, status, version, model, owner, team, release,
                prompt, tools, kb, sessions, success, p95, cost, updated);
        AgentModelBinder.bind(agent);
        store.put(id, agent);
    }

    public Optional<Agent> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    public Optional<Agent> findByCode(String code) {
        return store.values().stream().filter(a -> a.code().equals(code)).findFirst();
    }

    public List<Agent> findAll() {
        return List.copyOf(store.values());
    }

    public void save(Agent agent) {
        store.put(agent.id(), agent);
    }
}
