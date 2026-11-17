-- 迭代 01 复审修正：V11 已在本地库执行，新增列拆到 V13，避免 Flyway checksum 冲突。
-- 说明：只增列与索引，不改历史数据；旧消息 retrieval_json 为空，旧反馈 handle_status 默认 OPEN。

-- 检索诊断快照：命中数、最高分、检索模式、是否 0 命中；供 KQ-2 与复盘使用
ALTER TABLE session_messages ADD COLUMN retrieval_json TEXT;

-- 反馈处理状态扩展位：OPEN / HANDLED / IGNORED
ALTER TABLE message_feedback ADD COLUMN handle_status VARCHAR(16) NOT NULL DEFAULT 'OPEN';
-- 原因检索列（形如 ,CODE1,CODE2,），用于 SQL 分页过滤，避免依赖 JSON 函数
ALTER TABLE message_feedback ADD COLUMN reasons_csv VARCHAR(128);
-- 智能体名称快照，管理端列表直接展示，避免逐条回查会话
ALTER TABLE message_feedback ADD COLUMN agent_name VARCHAR(120);
CREATE INDEX idx_feedback_status_time ON message_feedback (handle_status, created_at);
