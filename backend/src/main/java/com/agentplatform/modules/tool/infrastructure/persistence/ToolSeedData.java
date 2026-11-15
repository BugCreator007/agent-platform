package com.agentplatform.modules.tool.infrastructure.persistence;

import com.agentplatform.modules.tool.domain.model.Tool;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具目录演示种子数据（infrastructure 层）。
 *
 * <p>以内存 {@link ConcurrentHashMap} 提供首次启动时的演示工具集合；只用于初始化，
 * 不作为运行时存储（运行时以 MyBatis-Plus 持久化仓储为准）。
 */
public class ToolSeedData {
    /** 名称 → 工具的内存种子表。 */
    private final Map<String, Tool> store = new ConcurrentHashMap<>();

    /**
     * 构造并写入预置的演示工具。
     */
    public ToolSeedData() {
        seed("order.query", "API", "交易平台", 128400, 0.2, "healthy", "2 分钟前", true, "按订单号查询订单状态、金额与履约信息");
        seed("refund.apply", "API", "交易平台", 42180, 0.4, "healthy", "2 分钟前", true, "发起退款预校验与正式退款");
        seed("kb.search", "内置", "知识库", 96320, 0.1, "healthy", "5 分钟前", true, "混合检索知识库，返回 Top-K 片段");
        seed("risk.score", "API", "风控", 18940, 1.2, "degraded", "12 分钟前", true, "查询账号与交易的风险评分");
        seed("refund.review", "MCP", "交易平台", 8410, 0.3, "healthy", "4 分钟前", true, "提交人工复核并回写结论");
        seed("bi.query", "API", "数据平台", 21600, 0.0, "healthy", "6 分钟前", true, "执行受限 SQL 查询经营指标");
        seed("notify.send", "内置", "消息中心", 33900, 0.6, "healthy", "8 分钟前", true, "发送站内信、短信或企微通知");
        seed("invoice.issue", "API", "财务中台", 12300, 0.1, "healthy", "3 分钟前", true, "开具电子发票与红冲");
        seed("crm.customer", "MCP", "CRM", 6200, 0.2, "healthy", "10 分钟前", true, "读取客户画像与历史工单");
        seed("web.fetch", "内置", "通用", 4880, 3.4, "degraded", "15 分钟前", false, "抓取公网页面正文，受域名白名单限制");
        seed("weather.query", "内置", "通用", 0, 0, "healthy", "刚刚", true, "按城市或地址查询当前实时天气");
    }

    /**
     * 写入一条种子工具。
     *
     * @param name        工具名称
     * @param type        工具类型
     * @param owner       归属团队
     * @param calls       累计调用次数
     * @param errorRate   错误率
     * @param health      健康状态
     * @param checked     最近检查时间文本
     * @param enabled     是否启用
     * @param description 工具描述
     */
    private void seed(String name, String type, String owner, long calls, double errorRate,
                      String health, String checked, boolean enabled, String description) {
        store.put(name, new Tool(name, type, owner, calls, errorRate, health, checked, enabled, description));
    }

    /**
     * 返回全部种子工具的不可变快照。
     *
     * @return 工具列表
     */
    public List<Tool> findAll() { return List.copyOf(store.values()); }

    /**
     * 按名称查询种子工具。
     *
     * @param name 工具名称
     * @return 种子工具；不存在时返回 {@link Optional#empty()}
     */
    public Optional<Tool> findByName(String name) { return Optional.ofNullable(store.get(name)); }

    /**
     * 写入或覆盖一条种子工具。
     *
     * @param tool 工具对象
     */
    public void save(Tool tool) { store.put(tool.name(), tool); }
}
