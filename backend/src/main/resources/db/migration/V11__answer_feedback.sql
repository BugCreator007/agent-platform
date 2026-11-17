-- KQ-1：回答反馈、无答案识别与最小会话归属
-- 说明：只新增字段与表，不改历史数据；answer_mode 默认 HYBRID，保持现网行为不变。

-- 会话归属：门户/调试创建会话时写入当前登录用户名，用于反馈鉴权，并为后续用户会话隔离打底
ALTER TABLE chat_sessions ADD COLUMN created_by VARCHAR(64);
CREATE INDEX idx_sessions_created_by ON chat_sessions (created_by);

-- 智能体回答模式：HYBRID 保持现状；STRICT 仅在知识库有命中时交给模型回答
ALTER TABLE agents ADD COLUMN answer_mode VARCHAR(16) NOT NULL DEFAULT 'HYBRID';

-- 助手消息类型与低置信标记：REFUSAL 表示严格模式下的拒答
ALTER TABLE session_messages ADD COLUMN answer_type VARCHAR(16) NOT NULL DEFAULT 'ANSWER';
ALTER TABLE session_messages ADD COLUMN low_confidence TINYINT NOT NULL DEFAULT 0;

-- 消息级反馈：同一用户对同一条助手消息只保留一条，重复提交由应用层更新
-- question_preview/answer_preview 为提交时的快照，便于管理端列表直接展示，避免回查长会话
CREATE TABLE message_feedback (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    message_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    agent_id VARCHAR(64),
    user_id VARCHAR(64) NOT NULL,
    rating VARCHAR(8) NOT NULL,
    reasons_json TEXT,
    comment VARCHAR(200),
    follow_up_requested TINYINT NOT NULL DEFAULT 0,
    question_preview VARCHAR(300),
    answer_preview VARCHAR(300),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_feedback_message FOREIGN KEY (message_id) REFERENCES session_messages(id) ON DELETE CASCADE,
    CONSTRAINT uk_feedback_message_user UNIQUE (message_id, user_id)
);
CREATE INDEX idx_feedback_agent_time ON message_feedback (agent_id, created_at);
CREATE INDEX idx_feedback_session ON message_feedback (session_id);
CREATE INDEX idx_feedback_rating_time ON message_feedback (rating, created_at);
