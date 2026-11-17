package com.agentplatform.modules.chat.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.agentplatform.modules.chat.infrastructure.persistence.TraceStepListTypeHandler;

import com.agentplatform.modules.chat.domain.model.ChatSession;
import com.agentplatform.modules.chat.domain.model.TraceStep;

import java.time.Instant;
import java.util.List;

/**
 * 会话表 {@code chat_sessions} 的持久化实体（infrastructure 层）。
 *
 * <p>表结构由 Flyway 管理；trace 以 JSON 字符串存储，通过 {@link TraceStepListTypeHandler}
 * 与领域对象互转。{@code created_at} 交由数据库默认值维护，插入/更新时永不写入。</p>
 */
@TableName(value = "chat_sessions", autoResultMap = true)
public class ChatSessionEntity {
    /** 会话 ID（主键，由应用侧生成，不使用数据库自增）。 */
    @TableId(type = IdType.INPUT) private String id;
    /** 所属智能体 ID。 */
    @TableField(value = "agent_id") private String agentId;
    /** 所属智能体名称（冗余字段，便于列表直接展示）。 */
    @TableField(value = "agent_name") private String agentName;
    /** 会话状态：running / success / failed / timeout / error。 */
    private String status;
    /** 对话轮次。 */
    private int turns;
    /** 工具调用次数。 */
    private int tools;
    /** 累计 token 估算值。 */
    private int tokens;
    /** 会话耗时（毫秒）。 */
    private long ms;
    /** 展示用时间标签，格式 HH:mm:ss。 */
    @TableField(value = "time_label") private String timeLabel;
    /** 会话主题，默认取首条用户消息。 */
    private String topic;
    /** 执行链路步骤，以 JSON 存储。 */
    @TableField(value = "trace_json", typeHandler = TraceStepListTypeHandler.class) private List<TraceStep> trace;
    /** 会话创建人登录名；历史会话为空，用于反馈鉴权与后续用户隔离。 */
    @TableField(value = "created_by") private String createdBy;
    /** 创建时间；由数据库默认值写入，应用侧只读。 */
    @TableField(value = "created_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER) private Instant createdAt;

    /**
     * 领域模型转持久化实体。
     *
     * @param s 领域会话聚合
     * @return 对应实体
     */
    public static ChatSessionEntity from(ChatSession s) {
        ChatSessionEntity e = new ChatSessionEntity();
        e.id = s.id(); e.agentId = s.agentId(); e.agentName = s.agentName(); e.status = s.status();
        e.turns = s.turns(); e.tools = s.tools(); e.tokens = s.tokens(); e.ms = s.ms(); e.timeLabel = s.time();
        e.topic = s.topic(); e.trace = s.trace(); e.createdBy = s.createdBy();
        return e;
    }

    /**
     * 持久化实体转领域模型；trace 为 null 时回退为空列表。
     *
     * @return 领域会话聚合
     */
    public ChatSession toDomain() {
        return new ChatSession(id, agentId, agentName, status, turns, tools, tokens, ms,
                timeLabel, topic, trace == null ? List.of() : trace, createdBy);
    }

    /**
     * @return 所属智能体 ID
     */
    public String getAgentId() { return agentId; }
    /**
     * @return 所属智能体名称
     */
    public String getAgentName() { return agentName; }
    /**
     * @return 会话状态
     */
    public String getStatus() { return status; }
    /**
     * @return 工具调用次数
     */
    public int getTools() { return tools; }
    /**
     * @return 累计 token 估算值
     */
    public int getTokens() { return tokens; }
    /**
     * @return 会话耗时（毫秒）
     */
    public long getMs() { return ms; }
    /**
     * @return 创建时间
     */
    public Instant getCreatedAt() { return createdAt; }
}
