CREATE TABLE chat_session (
  id CHAR(36) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  user_id VARCHAR(128) NOT NULL,
  title VARCHAR(255) NOT NULL,
  agent_id VARCHAR(255),
  agent_name_snapshot VARCHAR(255),
  status VARCHAR(32) NOT NULL,
  created_at TIMESTAMP(3) NOT NULL,
  updated_at TIMESTAMP(3) NOT NULL,
  INDEX idx_session_tenant_user (tenant_id, user_id, updated_at)
);

CREATE TABLE chat_message (
  id CHAR(36) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  session_id CHAR(36) NOT NULL,
  role VARCHAR(32) NOT NULL,
  content LONGTEXT NOT NULL,
  runtime_execution_id VARCHAR(128),
  status VARCHAR(32) NOT NULL,
  created_at TIMESTAMP(3) NOT NULL,
  INDEX idx_message_session (tenant_id, session_id, created_at)
);

CREATE TABLE chat_run (
  id CHAR(36) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  session_id CHAR(36) NOT NULL,
  agent_id VARCHAR(255) NOT NULL,
  runtime_execution_id VARCHAR(128),
  status VARCHAR(32) NOT NULL,
  error_message TEXT,
  started_at TIMESTAMP(3) NOT NULL,
  finished_at TIMESTAMP(3),
  INDEX idx_run_session (tenant_id, session_id, started_at)
);
