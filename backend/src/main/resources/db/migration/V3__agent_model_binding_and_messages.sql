ALTER TABLE agents ADD COLUMN provider_id VARCHAR(64);
ALTER TABLE agents ADD COLUMN model_id VARCHAR(128);
CREATE INDEX idx_agents_provider ON agents (provider_id);

CREATE TABLE session_messages (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    role VARCHAR(32) NOT NULL,
    content LONGTEXT NOT NULL,
    tokens INT NOT NULL DEFAULT 0,
    model VARCHAR(128),
    context_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_messages_session FOREIGN KEY (session_id) REFERENCES chat_sessions(id)
);
CREATE INDEX idx_session_messages_session ON session_messages (session_id, created_at);
