package com.schemaplatform.workflowchat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 消息实体。对应 chat_message 表。
 * runtime_execution_id 关联 Runtime 执行，role 为 user/assistant/system。
 */
@Entity
@Table(name = "chat_message")
public class ChatMessage {

  @Id
  @Column(name = "id", length = 36)
  private String id;

  @Column(name = "tenant_id", length = 64, nullable = false)
  private String tenantId;

  @Column(name = "session_id", length = 36, nullable = false)
  private String sessionId;

  @Enumerated(EnumType.STRING)
  @Column(name = "role", length = 32, nullable = false)
  private MessageRole role;

  @Column(name = "content", nullable = false, columnDefinition = "LONGTEXT")
  private String content;

  @Column(name = "runtime_execution_id", length = 128)
  private String runtimeExecutionId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", length = 32, nullable = false)
  private MessageStatus status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected ChatMessage() {}

  public static ChatMessage userMessage(String id, String tenantId, String sessionId, String content) {
    ChatMessage m = new ChatMessage();
    m.id = id;
    m.tenantId = tenantId;
    m.sessionId = sessionId;
    m.role = MessageRole.USER;
    m.content = content;
    m.status = MessageStatus.COMPLETED;
    m.createdAt = Instant.now();
    return m;
  }

  public static ChatMessage assistantPlaceholder(String id, String tenantId, String sessionId,
      String runtimeExecutionId) {
    ChatMessage m = new ChatMessage();
    m.id = id;
    m.tenantId = tenantId;
    m.sessionId = sessionId;
    m.role = MessageRole.ASSISTANT;
    m.content = "";
    m.runtimeExecutionId = runtimeExecutionId;
    m.status = MessageStatus.RUNNING;
    m.createdAt = Instant.now();
    return m;
  }

  public static ChatMessage assistantResult(String id, String tenantId, String sessionId,
      String content, String runtimeExecutionId, MessageStatus status) {
    ChatMessage m = new ChatMessage();
    m.id = id;
    m.tenantId = tenantId;
    m.sessionId = sessionId;
    m.role = MessageRole.ASSISTANT;
    m.content = content;
    m.runtimeExecutionId = runtimeExecutionId;
    m.status = status;
    m.createdAt = Instant.now();
    return m;
  }

  public static ChatMessage systemMessage(String id, String tenantId, String sessionId, String content) {
    ChatMessage m = new ChatMessage();
    m.id = id;
    m.tenantId = tenantId;
    m.sessionId = sessionId;
    m.role = MessageRole.SYSTEM;
    m.content = content;
    m.status = MessageStatus.COMPLETED;
    m.createdAt = Instant.now();
    return m;
  }

  public void updateResult(String content, MessageStatus status) {
    this.content = content;
    this.status = status;
  }

  public void updateStatus(MessageStatus status) {
    this.status = status;
  }

  // getters
  public String getId() { return id; }
  public String getTenantId() { return tenantId; }
  public String getSessionId() { return sessionId; }
  public MessageRole getRole() { return role; }
  public String getContent() { return content; }
  public String getRuntimeExecutionId() { return runtimeExecutionId; }
  public MessageStatus getStatus() { return status; }
  public Instant getCreatedAt() { return createdAt; }
  public void setContent(String content) { this.content = content; }

  public enum MessageRole {
    USER, ASSISTANT, SYSTEM
  }
}
