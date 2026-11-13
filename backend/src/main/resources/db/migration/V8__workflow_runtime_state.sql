ALTER TABLE workflow_instances ADD COLUMN state_json TEXT;
ALTER TABLE workflow_instances ADD COLUMN resume_at TIMESTAMP;
