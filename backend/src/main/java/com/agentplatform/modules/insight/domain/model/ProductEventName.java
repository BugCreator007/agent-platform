package com.agentplatform.modules.insight.domain.model;

/**
 * 产品埋点事件字典（领域枚举）。
 *
 * <p>{@code wireName} 是入库与前端上报使用的稳定编码，不能随意改名；
 * {@code clientAllowed} 标记是否允许客户端直接上报，其余事件只能由服务端写入。</p>
 */
public enum ProductEventName {
    /** 新会话创建 */
    SESSION_STARTED("session_started", false),
    /** 用户消息落库 */
    MESSAGE_SENT("message_sent", false),
    /** 助手回答完成 */
    ANSWER_COMPLETED("answer_completed", false),
    /** 回答失败或超时 */
    ANSWER_FAILED("answer_failed", false),
    /** 用户提交反馈 */
    FEEDBACK_SUBMITTED("feedback_submitted", false),
    /** 会话删除 */
    SESSION_DELETED("session_deleted", false),
    /** 用户打开智能体（客户端） */
    AGENT_OPENED("agent_opened", true),
    /** 用户复制回答（客户端） */
    ANSWER_COPIED("answer_copied", true),
    /** 用户下载引用（客户端） */
    CITATION_DOWNLOADED("citation_downloaded", true);

    /** 入库与上报使用的编码 */
    private final String wireName;
    /** 是否允许客户端上报 */
    private final boolean clientAllowed;

    ProductEventName(String wireName, boolean clientAllowed) {
        this.wireName = wireName;
        this.clientAllowed = clientAllowed;
    }

    /**
     * @return 入库编码
     */
    public String wireName() { return wireName; }

    /**
     * @return 允许客户端上报时返回 true
     */
    public boolean clientAllowed() { return clientAllowed; }

    /**
     * 按编码解析事件名。
     *
     * @param value 小写下划线编码
     * @return 对应枚举；未知或空值返回 null
     */
    public static ProductEventName fromWire(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        for (ProductEventName name : values()) {
            if (name.wireName.equals(normalized)) return name;
        }
        return null;
    }
}
