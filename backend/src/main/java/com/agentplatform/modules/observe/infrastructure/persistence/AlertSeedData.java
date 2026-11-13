package com.agentplatform.modules.observe.infrastructure.persistence;

import com.agentplatform.modules.observe.domain.model.Alert;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class AlertSeedData {
    private final Map<String, Alert> store = new ConcurrentHashMap<>();

    public AlertSeedData() {
        seed("al1", "critical", "工具错误率突增", "tool.err_rate > 1%", "risk.score · 退款审核 Copilot",
                "14:21", "11 分钟前", "active", "1.2% / 5min",
                "过去 5 分钟 risk.score 调用失败 42 次，上游返回 504。已自动降级为跳过风控分，转人工复核。");
        seed("al2", "warning", "P95 首响超过 2 秒", "latency.p95 > 2s", "商家经营周报",
                "14:12", "20 分钟前", "active", "2.41s / 10min",
                "bi.query 平均耗时升至 1.8s，疑似数据平台负载过高。");
        seed("al3", "warning", "知识库召回率下降", "kb.recall < 85%", "客服质检 · kb-ticket",
                "13:49", "43 分钟前", "ack", "81.3% / 1h", "kb-ticket 正在重建索引，召回率暂时下降。");
        seed("al4", "critical", "退款审批失败率上升", "session.fail_rate > 3%", "退款审核 Copilot",
                "13:30", "1 小时前", "resolved", "3.8% → 0.9%", "v12 灰度 10% 时失败率升高，已回退流量。");
        seed("al5", "info", "月度预算已使用 62%", "budget.used > 60%", "平台总成本",
                "12:00", "2 小时前", "resolved", "¥12,480 / ¥20,000", "按当前速度预计月底使用 78%。");
        seed("al6", "warning", "会话超时率上升", "session.timeout > 1%", "商家经营周报",
                "11:20", "3 小时前", "resolved", "1.4% → 0.2%", "数据平台慢查询导致，已通过限流恢复。");
    }
    private void seed(String id, String severity, String title, String rule, String target,
                      String time, String ago, String status, String metric, String detail) {
        store.put(id, new Alert(id, severity, title, rule, target, time, ago, status, metric, detail));
    }
    public List<Alert> findAll() { return List.copyOf(store.values()); }
    public Optional<Alert> findById(String id) { return Optional.ofNullable(store.get(id)); }
    public void save(Alert alert) { store.put(alert.id(), alert); }
}
