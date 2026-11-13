package com.agentplatform.modules.release.infrastructure.persistence;

import com.agentplatform.modules.release.domain.model.Release;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ReleaseSeedData {
    private final Map<String, Release> store = new ConcurrentHashMap<>();

    public ReleaseSeedData() {
        seed("r25", "a1", "售后工单助手", "v25", "canary", 20, 94.2, "顾先路", "2 小时前", "新增退款预校验工具，优化首响 18%");
        seed("r12", "a2", "退款审核 Copilot", "v12", "approve", 0, 97.2, "李婧", "35 分钟前", "接入 risk.score 新版本，调整高风险阈值");
        seed("r9", "a8", "对账异常助手", "v9", "canary", 5, 93.1, "顾先路", "1 天前", "新增渠道对账差异归因");
        seed("r26", "a1", "售后工单助手", "v26", "draft", 0, null, "顾先路", "今天 09:42", "周报摘要格式调整（未提交）");
        seed("r6", "a5", "风控问答", "v6", "rollback", 0, 89.4, "陈昊", "3 天前", "知识库引用错误率上升，自动回滚");
        seed("r19", "a6", "客服质检", "v19", "live", 100, 96.8, "李婧", "5 天前", "质检维度从 3 个扩展到 5 个");
    }

    private void seed(String id, String agentId, String agentName, String version, String stage,
                      int traffic, Double score, String owner, String time, String note) {
        store.put(id, new Release(id, agentId, agentName, version, stage, traffic, score, owner, time, note));
    }

    public List<Release> findAll() { return List.copyOf(store.values()); }
    public Optional<Release> findById(String id) { return Optional.ofNullable(store.get(id)); }
    public void save(Release release) { store.put(release.id(), release); }
}
