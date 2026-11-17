package com.agentplatform.modules.chat.application.query;

import java.time.Instant;
import java.util.List;

/**
 * 当前用户对一条消息的反馈视图（application/query 层）。
 *
 * <p>用户端刷新或换设备时据此恢复点赞/点踩高亮与原因。</p>
 *
 * @param id                反馈 ID
 * @param messageId         消息 ID
 * @param rating            UP / DOWN
 * @param reasons           原因列表
 * @param comment           备注
 * @param followUpRequested 是否请求知识运营跟进
 * @param createdAt         创建时间
 * @param updatedAt         更新时间
 */
public record FeedbackView(String id, String messageId, String rating,
                           List<FeedbackReasonView> reasons, String comment,
                           boolean followUpRequested, Instant createdAt, Instant updatedAt) {
}
