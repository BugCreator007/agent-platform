package com.agentplatform.modules.observe.domain.model;

/**
 * 告警领域对象（domain 层）。
 *
 * <p>表示一条平台告警，状态流转：active（新触发）→ ack（已确认）→ resolved（已恢复）。
 * 状态变更通过 {@link #ack()}、{@link #resolve()} 方法完成，保持规则在领域内。</p>
 */
public class Alert {
    /** 告警 ID。 */
    private final String id;
    /** 严重级别：critical/warning/info。 */
    private final String severity;
    /** 告警标题。 */
    private final String title;
    /** 关联规则/条件描述，如 “tool.err_rate > 1%”。 */
    private final String rule;
    /** 影响对象，如智能体或知识库名称。 */
    private final String target;
    /** 触发时间展示值（HH:mm）。 */
    private final String time;
    /** 相对时间展示值，如 “11 分钟前”。 */
    private final String ago;
    /** 当前状态：active/ack/resolved。 */
    private String status;
    /** 指标展示值，如 “1.2% / 5min”。 */
    private final String metric;
    /** 详细说明。 */
    private final String detail;

    /**
     * 构造告警。
     *
     * @param id       告警 ID
     * @param severity 严重级别
     * @param title    标题
     * @param rule     关联规则/条件
     * @param target   影响对象
     * @param time     触发时间展示值
     * @param ago      相对时间展示值
     * @param status   初始状态
     * @param metric   指标展示值
     * @param detail   详细说明
     */
    public Alert(String id, String severity, String title, String rule, String target, String time,
                 String ago, String status, String metric, String detail) {
        this.id = id; this.severity = severity; this.title = title; this.rule = rule;
        this.target = target; this.time = time; this.ago = ago; this.status = status;
        this.metric = metric; this.detail = detail;
    }

    /**
     * 确认告警（状态置为 ack）。
     */
    public void ack() { this.status = "ack"; }

    /**
     * 恢复告警（状态置为 resolved）。
     */
    public void resolve() { this.status = "resolved"; }

    /**
     * 告警 ID。
     *
     * @return 告警 ID
     */
    public String id() { return id; }

    /**
     * 严重级别。
     *
     * @return critical/warning/info
     */
    public String severity() { return severity; }

    /**
     * 告警标题。
     *
     * @return 标题
     */
    public String title() { return title; }

    /**
     * 关联规则/条件。
     *
     * @return 规则描述
     */
    public String rule() { return rule; }

    /**
     * 影响对象。
     *
     * @return 对象名称
     */
    public String target() { return target; }

    /**
     * 触发时间展示值。
     *
     * @return HH:mm
     */
    public String time() { return time; }

    /**
     * 相对时间展示值。
     *
     * @return 如 “11 分钟前”
     */
    public String ago() { return ago; }

    /**
     * 当前状态。
     *
     * @return active/ack/resolved
     */
    public String status() { return status; }

    /**
     * 指标展示值。
     *
     * @return 如 “1.2% / 5min”
     */
    public String metric() { return metric; }

    /**
     * 详细说明。
     *
     * @return 详情文本
     */
    public String detail() { return detail; }
}
