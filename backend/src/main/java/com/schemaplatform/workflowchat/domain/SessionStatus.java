package com.schemaplatform.workflowchat.domain;

/**
 * 会话状态。对应 chat_session.status。
 * 简化版：会话本身不承载执行态，执行态由 chat_run 表管理。
 */
public enum SessionStatus {
  ACTIVE,
  ARCHIVED
}
