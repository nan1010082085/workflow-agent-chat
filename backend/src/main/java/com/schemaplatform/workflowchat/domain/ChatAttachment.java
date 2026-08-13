package com.schemaplatform.workflowchat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 上传附件元数据。物理文件存于配置的 root-dir（默认 ~/payflow/agentChat）。
 */
@Entity
@Table(name = "chat_attachment")
public class ChatAttachment {

  @Id
  @Column(name = "id", length = 36)
  private String id;

  @Column(name = "tenant_id", length = 64, nullable = false)
  private String tenantId;

  @Column(name = "user_id", length = 128, nullable = false)
  private String userId;

  @Column(name = "session_id", length = 36)
  private String sessionId;

  @Column(name = "message_id", length = 36)
  private String messageId;

  @Column(name = "original_filename", length = 512, nullable = false)
  private String originalFilename;

  @Column(name = "stored_relative_path", length = 1024, nullable = false)
  private String storedRelativePath;

  @Column(name = "content_type", length = 128, nullable = false)
  private String contentType;

  @Column(name = "size_bytes", nullable = false)
  private long sizeBytes;

  @Column(name = "excerpt", columnDefinition = "TEXT")
  private String excerpt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected ChatAttachment() {}

  public static ChatAttachment create(
      String id,
      String tenantId,
      String userId,
      String sessionId,
      String originalFilename,
      String storedRelativePath,
      String contentType,
      long sizeBytes,
      String excerpt) {
    ChatAttachment a = new ChatAttachment();
    a.id = id;
    a.tenantId = tenantId;
    a.userId = userId;
    a.sessionId = sessionId;
    a.originalFilename = originalFilename;
    a.storedRelativePath = storedRelativePath;
    a.contentType = contentType;
    a.sizeBytes = sizeBytes;
    a.excerpt = excerpt;
    a.createdAt = Instant.now();
    return a;
  }

  public void bindMessage(String messageId, String sessionId) {
    this.messageId = messageId;
    if (sessionId != null && !sessionId.isBlank()) {
      this.sessionId = sessionId;
    }
  }

  public String getId() { return id; }
  public String getTenantId() { return tenantId; }
  public String getUserId() { return userId; }
  public String getSessionId() { return sessionId; }
  public String getMessageId() { return messageId; }
  public String getOriginalFilename() { return originalFilename; }
  public String getStoredRelativePath() { return storedRelativePath; }
  public String getContentType() { return contentType; }
  public long getSizeBytes() { return sizeBytes; }
  public String getExcerpt() { return excerpt; }
  public Instant getCreatedAt() { return createdAt; }
}
