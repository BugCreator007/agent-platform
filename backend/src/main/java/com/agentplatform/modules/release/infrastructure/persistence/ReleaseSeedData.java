package com.agentplatform.modules.release.infrastructure.persistence;

import com.agentplatform.modules.release.domain.model.Release;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 发布记录演示种子数据构造器（infrastructure 层）。
 * <p>仅在首次启动、releases 表为空时由 {@link ReleaseDataSeeder} 读取并落库，
 * 不是运行时存储。演示 id（r25/r12/...）与智能体种子数据 a1~a8 对应。</p>
 */
public class ReleaseSeedData {
    /** 种子数据内存容器，key 为发布单 id。 */
    private final Map<String, Release> store = new ConcurrentHashMap<>();

    /**
     * 构造 6 条覆盖 draft/approve/canary/live/rollback 各阶段的演示发布单。
     */
    public ReleaseSeedData() {
        seed("r25", "a1", "售后工单助手", "v25", "canary", 20, 94.2, "顾先路", "2 小时前", "新增退款预校验工具，优化首响 18%");
        seed("r12", "a2", "退款审核 Copilot", "v12", "approve", 0, 97.2, "李婧", "35 分钟前", "接入 risk.score 新版本，调整高风险阈值");
        seed("r9", "a8", "对账异常助手", "v9", "canary", 5, 93.1, "顾先路", "1 天前", "新增渠道对账差异归因");
        seed("r26", "a1", "售后工单助手", "v26", "draft", 0, null, "顾先路", "今天 09:42", "周报摘要格式调整（未提交）");
        seed("r6", "a5", "风控问答", "v6", "rollback", 0, 89.4, "陈昊", "3 天前", "知识库引用错误率上升，自动回滚");
        seed("r19", "a6", "客服质检", "v19", "live", 100, 96.8, "李婧", "5 天前", "质检维度从 3 个扩展到 5 个");
    }

    /**
     * 构造单条种子发布记录。
     *
     * @param id        发布单 id
     * @param agentId   关联智能体 id
     * @param agentName 智能体名称
     * @param version   版本号
     * @param stage     发布阶段
     * @param traffic   流量百分比
     * @param score     评测得分，可为 null
     * @param owner     操作人
     * @param time      时间文案
     * @param note      发布说明
     */
    private void seed(String id, String agentId, String agentName, String version, String stage,
                      int traffic, Double score, String owner, String time, String note) {
        store.put(id, new Release(id, agentId, agentName, version, stage, traffic, score, owner, time, note));
    }

    /**
     * @return 全部种子发布记录快照
     */
    public List<Release> findAll() { return List.copyOf(store.values()); }

    /**
     * @param id 发布单 id
     * @return 匹配的发布记录，不存在时为空
     */
    public Optional<Release> findById(String id) { return Optional.ofNullable(store.get(id)); }

    /**
     * 写入或覆盖内存容器中的记录（仅构造期使用）。
     *
     * @param release 发布记录
     */
    public void save(Release release) { store.put(release.id(), release); }
}
