ALTER TABLE providers ADD COLUMN protocol VARCHAR(32) NOT NULL DEFAULT 'openai';
ALTER TABLE providers ADD COLUMN api_key_encrypted TEXT;
ALTER TABLE models ADD COLUMN upstream_model VARCHAR(128);

UPDATE providers SET protocol = 'anthropic' WHERE id = 'anthropic';
UPDATE providers SET name = '千问 AI 平台', endpoint = 'https://dashscope.aliyuncs.com/compatible-mode/v1'
WHERE id = 'aliyun' AND name = '阿里云百炼';
UPDATE providers SET key_value = '未配置' WHERE api_key_encrypted IS NULL;

UPDATE models SET upstream_model = 'deepseek-flash' WHERE id = 'deepseek-v4';
UPDATE models SET upstream_model = 'deepseek-v4-pro' WHERE id = 'deepseek-r1';
UPDATE models SET upstream_model = id WHERE upstream_model IS NULL;
