package com.agentplatform.modules.chat.infrastructure.persistence;

import com.agentplatform.modules.chat.domain.model.ChatSession;
import com.agentplatform.modules.chat.domain.model.TraceStep;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class SessionSeedData {
    private final Map<String, ChatSession> store = new ConcurrentHashMap<>();

    public SessionSeedData() {
        seed("s_9f3a21", "a1", "售后工单助手", "success", 4, 2, 8420, 1180, "14:32:08",
                "订单 202611130097 想退款，但已签收", normalTrace());
        seed("s_9f3a1e", "a2", "退款审核 Copilot", "failed", 3, 1, 6180, 2400, "14:29:44",
                "退款金额与优惠券分摊不一致", failedTrace());
        seed("s_9f3a08", "a4", "发票助手", "success", 2, 0, 3050, 620, "14:26:17",
                "发票抬头写错了可以重开吗", normalTrace());
        seed("s_9f39f2", "a6", "客服质检", "success", 6, 3, 12900, 2100, "14:21:02",
                "质检会话 corp_88213", normalTrace());
        seed("s_9f39d7", "a1", "售后工单助手", "success", 3, 1, 7240, 1050, "14:18:33",
                "物流三天没有更新", normalTrace());
        seed("s_9f39c1", "a3", "商家经营周报", "timeout", 4, 2, 9880, 30000, "14:12:55",
                "生成 11 月第二周经营周报", normalTrace());
        seed("s_9f39a0", "a8", "对账异常助手", "running", 5, 2, 11200, 0, "14:08:21",
                "渠道 B 对账差异 1,204 元", normalTrace());
        seed("s_9f3987", "a1", "售后工单助手", "success", 2, 1, 5310, 940, "14:02:10",
                "修改收货地址", normalTrace());
        seed("s_9f3966", "a2", "退款审核 Copilot", "success", 5, 2, 10450, 1900, "13:56:47",
                "高风险账号退款复核", normalTrace());
        seed("s_9f3940", "a6", "客服质检", "failed", 2, 1, 4120, 1300, "13:49:05",
                "质检规则 R-204 判定", failedTrace());
        seed("s_9f3912", "a4", "发票助手", "success", 1, 0, 1860, 510, "13:41:36",
                "电子发票下载链接失效", normalTrace());
        seed("s_9f38f8", "a1", "售后工单助手", "success", 7, 4, 16700, 2600, "13:33:12",
                "批量退款进度查询", normalTrace());
    }

    private void seed(String id, String agentId, String agentName, String status, int turns,
                      int tools, int tokens, long ms, String time, String topic, List<TraceStep> trace) {
        store.put(id, new ChatSession(id, agentId, agentName, status, turns, tools, tokens, ms, time, topic, trace));
    }

    private List<TraceStep> normalTrace() {
        return List.of(
                new TraceStep("model", "意图识别", 180, 120, "ok", "识别意图：退款咨询 + 订单状态查询"),
                new TraceStep("tool", "order.query", 420, 0, "ok", "订单状态=已签收 · 实付 ¥268.00"),
                new TraceStep("kb", "知识检索 · kb-refund", 260, 0, "ok", "召回 3 条 · top1 相似度 0.91"),
                new TraceStep("tool", "refund.apply", 310, 0, "ok", "预校验通过，预计 1-3 个工作日退回"),
                new TraceStep("model", "生成回复", 540, 4100, "ok", "输出 186 字")
        );
    }

    private List<TraceStep> failedTrace() {
        return List.of(
                new TraceStep("model", "意图识别", 210, 140, "ok", "识别意图：退款金额争议"),
                new TraceStep("tool", "order.query", 380, 0, "ok", "订单实付 ¥368.00 · 优惠券分摊 ¥30.00"),
                new TraceStep("tool", "risk.score", 1800, 0, "fail", "调用超时（2000ms），重试 2 次均失败"),
                new TraceStep("model", "生成兜底", 0, 0, "skip", "前置工具失败，返回兜底话术")
        );
    }

    public List<ChatSession> findAll() {
        return List.copyOf(store.values());
    }

    public Optional<ChatSession> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    public void save(ChatSession session) {
        store.put(session.id(), session);
    }
}
