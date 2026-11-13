CREATE TABLE users (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(120),
    roles VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_users_username UNIQUE (username)
);

CREATE TABLE members (
    name VARCHAR(120) NOT NULL PRIMARY KEY,
    email VARCHAR(255),
    role VARCHAR(64) NOT NULL,
    agents INT NOT NULL DEFAULT 0,
    last_seen VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE api_keys (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    key_value VARCHAR(255) NOT NULL,
    scope VARCHAR(255) NOT NULL,
    created VARCHAR(32),
    last_used VARCHAR(64),
    created_by VARCHAR(120),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE audit_logs (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    actor VARCHAR(120),
    action VARCHAR(255) NOT NULL,
    object_name VARCHAR(255),
    occurred_at VARCHAR(64),
    ip VARCHAR(64),
    detail TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_audit_created ON audit_logs (created_at);
CREATE INDEX idx_audit_actor ON audit_logs (actor);

CREATE TABLE agents (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(120) NOT NULL,
    status VARCHAR(32) NOT NULL,
    version VARCHAR(32) NOT NULL,
    model VARCHAR(120),
    owner VARCHAR(120),
    team VARCHAR(120),
    release_status VARCHAR(64),
    prompt TEXT,
    tools TEXT,
    kb TEXT,
    sessions BIGINT NOT NULL DEFAULT 0,
    success DOUBLE NOT NULL DEFAULT 0,
    p95 VARCHAR(32),
    cost DOUBLE NOT NULL DEFAULT 0,
    updated VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_agents_code UNIQUE (code)
);

CREATE TABLE chat_sessions (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    agent_id VARCHAR(64),
    agent_name VARCHAR(120),
    status VARCHAR(32) NOT NULL,
    turns INT NOT NULL DEFAULT 0,
    tools INT NOT NULL DEFAULT 0,
    tokens INT NOT NULL DEFAULT 0,
    ms BIGINT NOT NULL DEFAULT 0,
    time_label VARCHAR(64),
    topic TEXT,
    trace_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_sessions_agent ON chat_sessions (agent_id);

CREATE TABLE datasets (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    owner VARCHAR(120),
    description TEXT,
    embedding_model VARCHAR(120),
    index_type VARCHAR(32),
    metric VARCHAR(32),
    parser VARCHAR(64),
    chunk_strategy VARCHAR(64),
    chunk_size INT NOT NULL DEFAULT 512,
    overlap INT NOT NULL DEFAULT 64,
    retrieval_mode VARCHAR(32),
    top_k INT NOT NULL DEFAULT 4,
    threshold DOUBLE NOT NULL DEFAULT 0.8,
    rerank BOOLEAN NOT NULL DEFAULT TRUE,
    needs_reindex BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(32),
    docs BIGINT NOT NULL DEFAULT 0,
    chunks BIGINT NOT NULL DEFAULT 0,
    updated VARCHAR(64),
    auth_agents TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE documents (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    dataset_id VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    version INT NOT NULL DEFAULT 1,
    chunks BIGINT NOT NULL DEFAULT 0,
    size_label VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    updated VARCHAR(64),
    storage_key VARCHAR(512),
    content_type VARCHAR(120),
    checksum VARCHAR(128),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_documents_dataset FOREIGN KEY (dataset_id) REFERENCES datasets(id)
);
CREATE INDEX idx_documents_dataset ON documents (dataset_id);

CREATE TABLE document_chunks (
    id VARCHAR(80) NOT NULL PRIMARY KEY,
    document_id VARCHAR(64) NOT NULL,
    dataset_id VARCHAR(64) NOT NULL,
    chunk_index INT NOT NULL,
    content TEXT NOT NULL,
    token_count INT NOT NULL DEFAULT 0,
    embedding_json LONGTEXT,
    metadata_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chunks_document FOREIGN KEY (document_id) REFERENCES documents(id)
);
CREATE INDEX idx_chunks_dataset ON document_chunks (dataset_id);
CREATE INDEX idx_chunks_document ON document_chunks (document_id);

CREATE TABLE providers (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    status VARCHAR(32) NOT NULL,
    endpoint VARCHAR(512),
    models BIGINT NOT NULL DEFAULT 0,
    calls BIGINT NOT NULL DEFAULT 0,
    share DOUBLE NOT NULL DEFAULT 0,
    latency VARCHAR(32),
    success DOUBLE NOT NULL DEFAULT 100,
    quota DOUBLE NOT NULL DEFAULT 0,
    used DOUBLE NOT NULL DEFAULT 0,
    key_value VARCHAR(512),
    region VARCHAR(120),
    route VARCHAR(120),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE models (
    id VARCHAR(128) NOT NULL PRIMARY KEY,
    provider_id VARCHAR(64) NOT NULL,
    name VARCHAR(120) NOT NULL,
    context_window VARCHAR(32),
    in_price DOUBLE NOT NULL DEFAULT 0,
    out_price DOUBLE NOT NULL DEFAULT 0,
    capabilities TEXT,
    calls BIGINT NOT NULL DEFAULT 0,
    latency VARCHAR(32),
    status VARCHAR(32) NOT NULL,
    route_weight INT NOT NULL DEFAULT 0,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_models_provider FOREIGN KEY (provider_id) REFERENCES providers(id)
);

CREATE TABLE routing_policy (
    id VARCHAR(32) NOT NULL PRIMARY KEY,
    strategy VARCHAR(64),
    primary_model VARCHAR(128),
    fallback TEXT,
    region VARCHAR(120),
    updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE releases (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    agent_id VARCHAR(64),
    agent_name VARCHAR(120),
    version VARCHAR(32),
    stage VARCHAR(32) NOT NULL,
    traffic INT NOT NULL DEFAULT 0,
    eval_score DOUBLE,
    owner VARCHAR(120),
    time_label VARCHAR(64),
    note TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE eval_suites (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    description TEXT,
    rate DOUBLE NOT NULL DEFAULT 0,
    cases INT NOT NULL DEFAULT 0,
    last_run VARCHAR(64)
);

CREATE TABLE eval_runs (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    suite VARCHAR(120),
    agent VARCHAR(120),
    version VARCHAR(32),
    passed VARCHAR(32),
    score DOUBLE NOT NULL DEFAULT 0,
    duration VARCHAR(32),
    time_label VARCHAR(64),
    status VARCHAR(32),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE tools (
    name VARCHAR(128) NOT NULL PRIMARY KEY,
    type VARCHAR(32) NOT NULL,
    owner VARCHAR(120),
    calls BIGINT NOT NULL DEFAULT 0,
    error_rate DOUBLE NOT NULL DEFAULT 0,
    health VARCHAR(32),
    checked VARCHAR(64),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    description TEXT,
    endpoint VARCHAR(512),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE workflows (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    status VARCHAR(32) NOT NULL,
    trigger_label VARCHAR(120),
    runs BIGINT NOT NULL DEFAULT 0,
    success DOUBLE NOT NULL DEFAULT 0,
    owner VARCHAR(120),
    version VARCHAR(32),
    updated VARCHAR(64),
    nodes_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE workflow_instances (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    workflow_id VARCHAR(64) NOT NULL,
    workflow_name VARCHAR(120),
    status VARCHAR(32) NOT NULL,
    trigger_type VARCHAR(64),
    input_json TEXT,
    output_json TEXT,
    current_node VARCHAR(64),
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    error TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_instances_workflow FOREIGN KEY (workflow_id) REFERENCES workflows(id)
);
CREATE INDEX idx_instances_workflow ON workflow_instances (workflow_id);

CREATE TABLE workflow_node_runs (
    id VARCHAR(80) NOT NULL PRIMARY KEY,
    instance_id VARCHAR(64) NOT NULL,
    node_key VARCHAR(64),
    node_type VARCHAR(32),
    title VARCHAR(120),
    status VARCHAR(32),
    input_json TEXT,
    output_json TEXT,
    attempt INT NOT NULL DEFAULT 1,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    error TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_node_runs_instance FOREIGN KEY (instance_id) REFERENCES workflow_instances(id)
);
CREATE INDEX idx_node_runs_instance ON workflow_node_runs (instance_id);

CREATE TABLE alerts (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    severity VARCHAR(32) NOT NULL,
    title VARCHAR(255) NOT NULL,
    rule_label VARCHAR(255),
    target VARCHAR(255),
    time_label VARCHAR(64),
    ago VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    metric VARCHAR(120),
    detail TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_alerts_status ON alerts (status);

CREATE TABLE usage_records (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    model VARCHAR(120),
    provider VARCHAR(120),
    agent_id VARCHAR(64),
    prompt_tokens INT NOT NULL DEFAULT 0,
    completion_tokens INT NOT NULL DEFAULT 0,
    cost DOUBLE NOT NULL DEFAULT 0,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_usage_occurred ON usage_records (occurred_at);
