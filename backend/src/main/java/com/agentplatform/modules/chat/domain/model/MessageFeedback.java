package com.agentplatform.modules.chat.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * 消息级反馈（domain 层）。
 *
 * <p>一条记录对应“某个用户对某条助手回答”的一次评价，按 {@code (messageId, userId)} 唯一。
 * 问题与回答预览是提交时的快照，管理端列表直接展示，不回查可能很长的会话。</p>
 *
 * @param id                反馈 ID，形如 {@code fb_xxxxxxxxxxxx}
 * @param messageId         被反馈的助手消息 ID
 * @param sessionId         所属会话 ID
 * @param agentId           所属智能体 ID
 * @param userId            反馈人登录名
 * @param rating            评分：UP / DOWN
 * @param reasons           原因列表；DOWN 必填 1–2 个
 * @param comment           可选备注，最长 200 字
 * @param followUpRequested 是否通过“留下问题”请求知识运营跟进
 * @param questionPreview   提问摘要快照
 * @param answerPreview     回答摘要快照
 * @param createdAt         创建时间
 * @param updatedAt         最后更新时间
 * @param handleStatus      处理状态：OPEN / HANDLED / IGNORED
 * @param agentName         提交时的智能体名称快照，管理端列表直接展示
 */
public record MessageFeedback(
        String id, String messageId, String sessionId, String agentId, String userId,
        FeedbackRating rating, List<FeedbackReason> reasons, String comment,
        boolean followUpRequested, String questionPreview, String answerPreview,
        Instant createdAt, Instant updatedAt, FeedbackHandleStatus handleStatus, String agentName
) {
    /**
     * 兼容未携带处理状态与智能体名称的调用方：默认 OPEN、无名称。
     */
    public MessageFeedback(String id, String messageId, String sessionId, String agentId, String userId,
                           FeedbackRating rating, List<FeedbackReason> reasons, String comment,
                           boolean followUpRequested, String questionPreview, String answerPreview,
                           Instant createdAt, Instant updatedAt) {
        this(id, messageId, sessionId, agentId, userId, rating, reasons, comment, followUpRequested,
                questionPreview, answerPreview, createdAt, updatedAt, FeedbackHandleStatus.OPEN, null);
    }

    /**
     * 兼容已携带处理状态的调用方：智能体名称默认为空。
     */
    public MessageFeedback(String id, String messageId, String sessionId, String agentId, String userId,
                           FeedbackRating rating, List<FeedbackReason> reasons, String comment,
                           boolean followUpRequested, String questionPreview, String answerPreview,
                           Instant createdAt, Instant updatedAt, FeedbackHandleStatus handleStatus) {
        this(id, messageId, sessionId, agentId, userId, rating, reasons, comment, followUpRequested,
                questionPreview, answerPreview, createdAt, updatedAt, handleStatus, null);
    }

    /**
     * 用于仓储读回时兜底原因列表，避免 JSON 解析失败导致 null。
     *
     * @return 不可变原因列表
     */
    public List<FeedbackReason> safeReasons() {
        return reasons == null ? List.of() : List.copyOf(reasons);
    }
}
