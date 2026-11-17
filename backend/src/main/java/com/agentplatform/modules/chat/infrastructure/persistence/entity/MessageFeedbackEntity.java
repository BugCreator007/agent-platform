package com.agentplatform.modules.chat.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.agentplatform.modules.chat.domain.model.FeedbackHandleStatus;
import com.agentplatform.modules.chat.domain.model.FeedbackRating;
import com.agentplatform.modules.chat.domain.model.FeedbackReason;
import com.agentplatform.modules.chat.domain.model.MessageFeedback;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 消息反馈表 {@code message_feedback} 的持久化实体（infrastructure 层）。
 *
 * <p>原因编码以 JSON 数组存储；问题/回答预览为提交时快照。实体只负责数据库与领域对象互转，
 * 原因合法性由应用服务在写入前校验。</p>
 */
@TableName(value = "message_feedback", autoResultMap = true)
public class MessageFeedbackEntity {
    /** 原因 JSON 序列化器；只用于字符串与枚举列表互转。 */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 主键，由应用层生成。 */
    @TableId(type = IdType.INPUT) private String id;
    /** 被反馈的助手消息 ID。 */
    @TableField(value = "message_id") private String messageId;
    /** 所属会话 ID。 */
    @TableField(value = "session_id") private String sessionId;
    /** 所属智能体 ID。 */
    @TableField(value = "agent_id") private String agentId;
    /** 提交时的智能体名称快照。 */
    @TableField(value = "agent_name") private String agentName;
    /** 反馈人登录名。 */
    @TableField(value = "user_id") private String userId;
    /** 评分：UP / DOWN。 */
    private String rating;
    /** 原因编码 JSON 数组；可为空，更新时允许清空。 */
    @TableField(value = "reasons_json", updateStrategy = FieldStrategy.ALWAYS) private String reasonsJson;
    /** 原因编码检索列（形如 ,CODE1,CODE2,），用于可按索引过滤的 LIKE 查询。 */
    @TableField(value = "reasons_csv", updateStrategy = FieldStrategy.ALWAYS) private String reasonsCsv;
    /** 可选备注；更新时允许清空。 */
    @TableField(value = "comment", updateStrategy = FieldStrategy.ALWAYS) private String comment;
    /** 是否请求知识运营跟进。 */
    @TableField(value = "follow_up_requested") private boolean followUpRequested;
    /** 处理状态：OPEN / HANDLED / IGNORED。 */
    @TableField(value = "handle_status") private String handleStatus;
    /** 提问摘要快照。 */
    @TableField(value = "question_preview") private String questionPreview;
    /** 回答摘要快照。 */
    @TableField(value = "answer_preview") private String answerPreview;
    /** 创建时间。 */
    @TableField(value = "created_at") private Instant createdAt;
    /** 最后更新时间。 */
    @TableField(value = "updated_at") private Instant updatedAt;

    /**
     * 领域对象转持久化实体。
     *
     * @param f 反馈记录
     * @return 对应实体
     */
    public static MessageFeedbackEntity from(MessageFeedback f) {
        MessageFeedbackEntity e = new MessageFeedbackEntity();
        e.id = f.id(); e.messageId = f.messageId(); e.sessionId = f.sessionId(); e.agentId = f.agentId();
        e.agentName = f.agentName();
        e.userId = f.userId(); e.rating = f.rating() == null ? null : f.rating().name();
        e.comment = f.comment(); e.followUpRequested = f.followUpRequested();
        e.handleStatus = (f.handleStatus() == null ? FeedbackHandleStatus.OPEN : f.handleStatus()).name();
        e.questionPreview = f.questionPreview(); e.answerPreview = f.answerPreview();
        e.createdAt = f.createdAt() == null ? Instant.now() : f.createdAt();
        e.updatedAt = f.updatedAt() == null ? e.createdAt : f.updatedAt();
        try {
            e.reasonsJson = f.safeReasons().isEmpty() ? null
                    : MAPPER.writeValueAsString(f.safeReasons().stream().map(Enum::name).toList());
            e.reasonsCsv = f.safeReasons().isEmpty() ? null
                    : "," + f.safeReasons().stream().map(Enum::name).collect(java.util.stream.Collectors.joining(",")) + ",";
        } catch (Exception ignored) {
            e.reasonsJson = null;
            e.reasonsCsv = null;
        }
        return e;
    }

    /**
     * 持久化实体转领域对象；非法原因编码会被忽略，避免脏数据阻断整条记录读取。
     *
     * @return 反馈记录
     */
    public MessageFeedback toDomain() {
        List<FeedbackReason> reasons = new ArrayList<>();
        try {
            if (reasonsJson != null && !reasonsJson.isBlank()) {
                for (String code : MAPPER.readValue(reasonsJson, new TypeReference<List<String>>() {})) {
                    FeedbackReason reason = FeedbackReason.parse(code);
                    if (reason != null) reasons.add(reason);
                }
            }
        } catch (Exception ignored) {
            // 原因 JSON 损坏时保留空列表；评分与备注仍可用于展示
        }
        return new MessageFeedback(id, messageId, sessionId, agentId, userId,
                FeedbackRating.parse(rating), reasons, comment, followUpRequested,
                questionPreview, answerPreview, createdAt, updatedAt,
                FeedbackHandleStatus.parse(handleStatus), agentName);
    }

    /** @return 主键 */
    public String getId() { return id; }
    /** 供重复键兜底时复用既有记录主键 */
    public void setId(String id) { this.id = id; }
    /** @return 消息 ID */
    public String getMessageId() { return messageId; }
    /** @return 会话 ID */
    public String getSessionId() { return sessionId; }
    /** @return 反馈人 */
    public String getUserId() { return userId; }
}
