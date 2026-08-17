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

  /** 助手思考过程（可选）。 */
  @Column(name = "thinking", columnDefinition = "LONGTEXT")
  private String thinking;
  /** 提示信息（可选）。 */
  @Column(name = "tip", columnDefinition = "TEXT")
  private String tip;

  /** 工具调用记录（JSON）。 */
  @Column(name = "tool_calls", columnDefinition = "JSON")
  private String toolCallsJson;

  /** 文档摘要（JSON）。 */
  @Column(name = "document_summaries", columnDefinition = "JSON")
  private String documentSummariesJson;

  /** 工作流执行详情（JSON）。 */
  @Column(name = "workflow_execution", columnDefinition = "JSON")
  private String workflowExecutionJson;


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
    m.thinking = null;
    m.runtimeExecutionId = runtimeExecutionId;
    m.status = MessageStatus.RUNNING;
    m.createdAt = Instant.now();
    return m;
  }

  public static ChatMessage assistantResult(String id, String tenantId, String sessionId,
      String content, String thinking, String runtimeExecutionId, MessageStatus status) {
    ChatMessage m = new ChatMessage();
    m.id = id;
    m.tenantId = tenantId;
    m.sessionId = sessionId;
    m.role = MessageRole.ASSISTANT;
    m.content = content == null ? "" : content;
    m.thinking = blankToNull(thinking);
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

  public void updateResult(String content, String thinking, MessageStatus status) {
    this.content = content == null ? "" : content;
    if (thinking != null && !thinking.isBlank()) {
      this.thinking = thinking;
    }
    this.status = status;
  }

  public void updateStatus(MessageStatus status) {
    this.status = status;
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  public String getId() { return id; }
  public String getTenantId() { return tenantId; }
  public String getSessionId() { return sessionId; }
  public MessageRole getRole() { return role; }
  public String getContent() { return content; }
  public String getThinking() { return thinking; }
  public String getTip() { return tip; }
  public void setTip(String tip) { this.tip = tip; }
  public String getToolCallsJson() { return toolCallsJson; }
  public void setToolCallsJson(String toolCallsJson) { this.toolCallsJson = toolCallsJson; }
  public String getDocumentSummariesJson() { return documentSummariesJson; }
  public void setDocumentSummariesJson(String documentSummariesJson) { this.documentSummariesJson = documentSummariesJson; }
  public String getWorkflowExecutionJson() { return workflowExecutionJson; }
  public void setWorkflowExecutionJson(String workflowExecutionJson) { this.workflowExecutionJson = workflowExecutionJson; }

  public String getRuntimeExecutionId() { return runtimeExecutionId; }
  public MessageStatus getStatus() { return status; }
  public Instant getCreatedAt() { return createdAt; }
  public void setContent(String content) { this.content = content; }
  public void setThinking(String thinking) { this.thinking = blankToNull(thinking); }

  public enum MessageRole {
    USER, ASSISTANT, SYSTEM
  }
}