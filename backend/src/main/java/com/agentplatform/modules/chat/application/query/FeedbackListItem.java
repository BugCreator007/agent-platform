package com.agentplatform.modules.chat.application.query;

import java.time.Instant;
import java.util.List;

/**
 * 管理端反馈列表项（application/query 层）。
 *
 * <p>问题与回答为提交时快照，默认由服务端截断，避免列表泄露完整对话内容。</p>
 */
public record FeedbackListItem(
        String id, String messageId, String sessionId, String agentId, String agentName,
        String userId, String rating, List<FeedbackReasonView> reasons, String comment,
        boolean followUpRequested, String questionPreview, String answerPreview, Instant createdAt,
        String handleStatus
) {
}
