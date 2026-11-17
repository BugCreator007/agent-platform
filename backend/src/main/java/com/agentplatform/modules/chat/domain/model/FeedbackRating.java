package com.agentplatform.modules.chat.domain.model;

/**
 * 消息反馈评分（domain 层）。
 *
 * <p>只有点赞与点踩两种取值；原因编码的合法性与评分绑定，
 * 由 {@link FeedbackReason#allowedFor(FeedbackRating)} 统一约束。</p>
 */
public enum FeedbackRating {
    /** 有用 */
    UP,
    /** 没用 */
    DOWN;

    /**
     * 解析评分字符串。
     *
     * @param value 前端传入的评分（忽略大小写）
     * @return 对应枚举；为空或非法时返回 null
     */
    public static FeedbackRating parse(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
