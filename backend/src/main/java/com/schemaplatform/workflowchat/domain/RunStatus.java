package com.schemaplatform.workflowchat.domain;

/**
 * Run 状态。对应 chat_run.status。
 * 与 Runtime 状态映射对齐（ARCHITECTURE §5）：
 * running -> RUNNING, success -> COMPLETED, error -> FAILED,
 * waiting -> WAITING_INPUT, cancelled -> CANCELLED。
 */
public enum RunStatus {
  RUNNING,
  COMPLETED,
  FAILED,
  WAITING_INPUT,
  CANCELLED
}
