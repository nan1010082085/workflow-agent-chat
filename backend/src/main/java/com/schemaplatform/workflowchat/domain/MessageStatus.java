package com.schemaplatform.workflowchat.domain;

/**
 * 消息状态。对应 chat_message.status。
 * 与 PRD §11 交互状态对齐：idle 由空消息体现，其余为显式状态。
 */
public enum MessageStatus {
  PENDING,
  RUNNING,
  WAITING_INPUT,
  COMPLETED,
  FAILED,
  CANCELLED
}
