ALTER TABLE agents ADD COLUMN citation_kb TEXT;
UPDATE agents SET citation_kb = kb WHERE citation_kb IS NULL;
ALTER TABLE session_messages ADD COLUMN citations_json TEXT;
