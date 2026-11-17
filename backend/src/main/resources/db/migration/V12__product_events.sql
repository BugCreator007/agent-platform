-- B0 产品埋点基线：事件只增不改，不记录问题/回答原文与 PII
-- event_id 由服务端生成或客户端提供，唯一约束用于重试幂等
CREATE TABLE product_events (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL,
    event_name VARCHAR(64) NOT NULL,
    user_id VARCHAR(64),
    session_id VARCHAR(64),
    message_id VARCHAR(64),
    agent_id VARCHAR(64),
    properties_json TEXT,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_events_event_id UNIQUE (event_id)
);
CREATE INDEX idx_events_name_time ON product_events (event_name, occurred_at);
CREATE INDEX idx_events_session ON product_events (session_id);
CREATE INDEX idx_events_agent_time ON product_events (agent_id, occurred_at);
CREATE INDEX idx_events_user_time ON product_events (user_id, occurred_at);
