CREATE TABLE mcp_servers (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    transport VARCHAR(32) NOT NULL,
    url VARCHAR(1024),
    command VARCHAR(512),
    args TEXT,
    auth_type VARCHAR(32),
    secret_encrypted TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'DISCONNECTED',
    last_error TEXT,
    last_connected_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE mcp_tools (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    server_id VARCHAR(64) NOT NULL,
    tool_key VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    input_schema LONGTEXT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    risk_level VARCHAR(32) NOT NULL DEFAULT 'read',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_mcp_tools_key UNIQUE (tool_key),
    CONSTRAINT fk_mcp_tools_server FOREIGN KEY (server_id) REFERENCES mcp_servers(id)
);
CREATE INDEX idx_mcp_tools_server ON mcp_tools (server_id);

CREATE TABLE mcp_tool_calls (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    server_id VARCHAR(64) NOT NULL,
    tool_key VARCHAR(255) NOT NULL,
    tool_name VARCHAR(255) NOT NULL,
    request_json LONGTEXT,
    response_json LONGTEXT,
    status VARCHAR(32) NOT NULL,
    duration_ms BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_mcp_calls_server ON mcp_tool_calls (server_id);
