package com.agentplatform.modules.chat.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import com.agentplatform.modules.chat.domain.model.MessageCitation;
import com.agentplatform.modules.chat.domain.model.RetrievalDiagnostics;
import com.agentplatform.modules.chat.domain.model.SessionMessage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

/**
 * 会话消息表 {@code session_messages} 的持久化实体（infrastructure 层）。
 *
 * <p>一条记录对应一轮对话中的一条消息（user 或 assistant），按 {@code created_at} 升序读取。</p>
 */
@TableName(value = "session_messages", autoResultMap = true)
public class SessionMessageEntity {
    /** 引用 JSON 序列化器；实体只负责 JSON 字符串与领域列表互转。 */
    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** 消息 ID（主键，由应用侧生成，不使用数据库自增）。 */
    @TableId(type = IdType.INPUT) private String id;
    /** 所属会话 ID。 */
    @TableField(value = "session_id") private String sessionId;
    /** 消息角色：user / assistant。 */
    private String role;
    /** 消息正文。 */
    private String content;
    /** 本条消息的 token 估算值。 */
    private int tokens;
    /** 生成该消息使用的模型名；用户消息为 null。 */
    private String model;
    /** 生成该消息时注入的 RAG 上下文条数。 */
    @TableField(value = "context_count") private int contextCount;
    /** 创建时间。 */
    @TableField(value = "created_at") private Instant createdAt;
    /** 知识库引用 JSON；历史数据为空。 */
    @TableField(value = "citations_json") private String citationsJson;
    /** 助手消息类型：ANSWER / REFUSAL；用户消息默认 ANSWER。 */
    @TableField(value = "answer_type") private String answerType;
    /** 低置信标记：绑定知识库但本轮 0 命中时为 true。 */
    @TableField(value = "low_confidence") private boolean lowConfidence;
    /** 检索诊断 JSON：命中数、最高分、模式、是否 0 命中。 */
    @TableField(value = "retrieval_json") private String retrievalJson;

    /**
     * 领域模型转持久化实体；创建时间为空时补当前时间。
     *
     * @param m 领域消息
     * @return 对应实体
     */
    public static SessionMessageEntity from(SessionMessage m) {
        SessionMessageEntity e = new SessionMessageEntity();
        e.id=m.id(); e.sessionId=m.sessionId(); e.role=m.role(); e.content=m.content();
        e.tokens=m.tokens(); e.model=m.model(); e.contextCount=m.contextCount();
        e.createdAt=m.createdAt() == null ? Instant.now() : m.createdAt();
        e.answerType=m.answerType() == null ? "ANSWER" : m.answerType();
        e.lowConfidence=m.lowConfidence();
        try { e.citationsJson = m.citations() == null || m.citations().isEmpty()
                ? null : MAPPER.writeValueAsString(m.citations()); }
        catch (Exception ignored) { e.citationsJson = null; }
        try { e.retrievalJson = m.retrieval() == null ? null : MAPPER.writeValueAsString(m.retrieval()); }
        catch (Exception ignored) { e.retrievalJson = null; }
        return e;
    }
    /**
     * 持久化实体转领域模型。
     *
     * @return 领域消息
     */
    public SessionMessage toDomain() {
        List<MessageCitation> citations;
        try { citations = citationsJson == null || citationsJson.isBlank() ? List.of()
                : MAPPER.readValue(citationsJson, new TypeReference<>() {}); }
        catch (Exception ignored) { citations = List.of(); }
        RetrievalDiagnostics retrieval = null;
        try { if (retrievalJson != null && !retrievalJson.isBlank()) {
                retrieval = MAPPER.readValue(retrievalJson, RetrievalDiagnostics.class); } }
        catch (Exception ignored) { retrieval = null; }
        return new SessionMessage(id, sessionId, role, content, tokens, model, contextCount, createdAt, citations,
                answerType == null ? "ANSWER" : answerType, lowConfidence, retrieval);
    }
}
