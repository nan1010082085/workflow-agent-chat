-- 会话消息附件元数据；物理文件存 chat.upload.root-dir
CREATE TABLE chat_attachment (
  id CHAR(36) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  user_id VARCHAR(128) NOT NULL,
  session_id CHAR(36) NULL,
  message_id CHAR(36) NULL,
  original_filename VARCHAR(512) NOT NULL,
  stored_relative_path VARCHAR(1024) NOT NULL,
  content_type VARCHAR(128) NOT NULL,
  size_bytes BIGINT NOT NULL,
  excerpt TEXT NULL,
  created_at TIMESTAMP(3) NOT NULL,
  INDEX idx_attachment_message (tenant_id, message_id),
  INDEX idx_attachment_session (tenant_id, session_id),
  INDEX idx_attachment_user (tenant_id, user_id, created_at)
);
