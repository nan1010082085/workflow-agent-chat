-- 对齐 Hibernate 期望的 VARCHAR(36)，与 V2 会话/消息 ID 类型一致
ALTER TABLE chat_attachment
  MODIFY COLUMN id VARCHAR(36) NOT NULL,
  MODIFY COLUMN session_id VARCHAR(36) NULL,
  MODIFY COLUMN message_id VARCHAR(36) NULL;
