ALTER TABLE documents ADD COLUMN source_type VARCHAR(32) NOT NULL DEFAULT 'FILE';
ALTER TABLE documents ADD COLUMN source_url VARCHAR(1024);
CREATE INDEX idx_documents_source ON documents (source_type);
