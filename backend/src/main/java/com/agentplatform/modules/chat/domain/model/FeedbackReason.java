package com.agentplatform.modules.chat.domain.model;

/**
 * 反馈原因编码（domain 层）。
 *
 * <p>编码会持久化到 {@code message_feedback.reasons_json}，因此不能随意改名；
 * 中文文案只用于展示。点赞原因可选，点踩原因必填 1–2 个，为 KQ-2 的缺口聚类提供统一口径。</p>
 */
public enum FeedbackReason {
    /** 答非所问（点踩） */
    NOT_RELEVANT("答非所问", FeedbackRating.DOWN),
    /** 不完整（点踩） */
    INCOMPLETE("不完整", FeedbackRating.DOWN),
    /** 信息过时（点踩） */
    OUTDATED("信息过时", FeedbackRating.DOWN),
    /** 依据不对（点踩） */
    WRONG_SOURCE("依据不对", FeedbackRating.DOWN),
    /** 没有依据（点踩） */
    NO_SOURCE("没有依据", FeedbackRating.DOWN),
    /** 其他（点踩） */
    OTHER("其他", FeedbackRating.DOWN),
    /** 解决了问题（点赞） */
    SOLVED("解决了问题", FeedbackRating.UP),
    /** 清晰易读（点赞） */
    CLEAR("清晰易读", FeedbackRating.UP),
    /** 引用有用（点赞） */
    SOURCE_USEFUL("引用有用", FeedbackRating.UP);

    /** 展示文案 */
    private final String label;
    /** 适用评分 */
    private final FeedbackRating rating;

    FeedbackReason(String label, FeedbackRating rating) {
        this.label = label;
        this.rating = rating;
    }

    /** @return 展示文案 */
    public String label() { return label; }

    /**
     * 判断该原因是否属于指定评分。
     *
     * @param candidate 评分
     * @return 属于时返回 true
     */
    public boolean allowedFor(FeedbackRating candidate) {
        return rating == candidate;
    }

    /**
     * 解析原因编码；非法值返回 null，由应用层统一报参数错误。
     *
     * @param code 原因编码
     * @return 对应枚举或 null
     */
    public static FeedbackReason parse(String code) {
        if (code == null || code.isBlank()) return null;
        try {
            return valueOf(code.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
