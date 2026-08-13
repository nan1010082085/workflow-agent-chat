package com.schemaplatform.workflowchat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 会话实体。对应 chat_session 表。
 * agent_id / agent_name_snapshot 在创建时快照，避免历史会话随 Agent 改名失真。
 */
@Entity
@Table(name = "chat_session")
public class ChatSession {

  @Id
  @Column(name = "id", length = 36)
  private String id;

  @Column(name = "tenant_id", length = 64, nullable = false)
  private String tenantId;

  @Column(name = "user_id", length = 128, nullable = false)
  private String userId;

  @Column(name = "title", length = 255, nullable = false)
  private String title;

  @Column(name = "agent_id", length = 255)
  private String agentId;

  @Column(name = "agent_name_snapshot", length = 255)
  private String agentNameSnapshot;

  /** 平台 LangGraph AIConversation id（Mongo ObjectId），模型模式 WS 多轮复用 */
  @Column(name = "platform_conversation_id", length = 64)
  private String platformConversationId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", length = 32, nullable = false)
  private SessionStatus status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  // JPA 需要无参构造
  protected ChatSession() {}

  public static ChatSession create(String id, String tenantId, String userId, String title,
      String agentId, String agentNameSnapshot) {
    ChatSession s = new ChatSession();
    s.id = id;
    s.tenantId = tenantId;
    s.userId = userId;
    s.title = title;
    s.agentId = agentId;
    s.agentNameSnapshot = agentNameSnapshot;
    s.status = SessionStatus.ACTIVE;
    Instant now = Instant.now();
    s.createdAt = now;
    s.updatedAt = now;
    return s;
  }

  public void touch() {
    this.updatedAt = Instant.now();
  }

  public void archive() {
    this.status = SessionStatus.ARCHIVED;
    this.updatedAt = Instant.now();
  }

  public void updateTitle(String newTitle) {
    this.title = newTitle;
    this.updatedAt = Instant.now();
  }

  public boolean belongsTo(String tenantId, String userId) {
    return this.tenantId.equals(tenantId) && this.userId.equals(userId);
  }

  // getters
  public String getId() { return id; }
  public String getTenantId() { return tenantId; }
  public String getUserId() { return userId; }
  public String getTitle() { return title; }
  public String getAgentId() { return agentId; }
  public String getAgentNameSnapshot() { return agentNameSnapshot; }
  public String getPlatformConversationId() { return platformConversationId; }
  public SessionStatus getStatus() { return status; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }

  public void setAgentId(String agentId) { this.agentId = agentId; }
  public void setAgentNameSnapshot(String agentNameSnapshot) { this.agentNameSnapshot = agentNameSnapshot; }

  public void setPlatformConversationId(String platformConversationId) {
    if (platformConversationId == null || platformConversationId.isBlank()) return;
    this.platformConversationId = platformConversationId.trim();
    this.updatedAt = Instant.now();
  }
}
