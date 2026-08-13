package com.schemaplatform.workflowchat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Run 实体。对应 chat_run 表。
 * 记录一次 Runtime 执行的映射与状态。runtime_execution_id 关联 Runtime execution。
 */
@Entity
@Table(name = "chat_run")
public class ChatRun {

  @Id
  @Column(name = "id", length = 36)
  private String id;

  @Column(name = "tenant_id", length = 64, nullable = false)
  private String tenantId;

  @Column(name = "session_id", length = 36, nullable = false)
  private String sessionId;

  @Column(name = "agent_id", length = 255, nullable = false)
  private String agentId;

  @Column(name = "runtime_execution_id", length = 128)
  private String runtimeExecutionId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", length = 32, nullable = false)
  private RunStatus status;

  @Column(name = "error_message", columnDefinition = "TEXT")
  private String errorMessage;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "finished_at")
  private Instant finishedAt;

  protected ChatRun() {}

  public static ChatRun start(String id, String tenantId, String sessionId, String agentId,
      String runtimeExecutionId) {
    ChatRun r = new ChatRun();
    r.id = id;
    r.tenantId = tenantId;
    r.sessionId = sessionId;
    r.agentId = agentId;
    r.runtimeExecutionId = runtimeExecutionId;
    r.status = RunStatus.RUNNING;
    r.startedAt = Instant.now();
    return r;
  }

  public void markCompleted() {
    this.status = RunStatus.COMPLETED;
    this.finishedAt = Instant.now();
  }

  public void markFailed(String errorMessage) {
    this.status = RunStatus.FAILED;
    this.errorMessage = errorMessage;
    this.finishedAt = Instant.now();
  }

  public void markWaiting() {
    this.status = RunStatus.WAITING_INPUT;
  }

  public void markCancelled() {
    this.status = RunStatus.CANCELLED;
    this.finishedAt = Instant.now();
  }

  public boolean isTerminal() {
    return status == RunStatus.COMPLETED || status == RunStatus.FAILED || status == RunStatus.CANCELLED;
  }

  // getters
  public String getId() { return id; }
  public String getTenantId() { return tenantId; }
  public String getSessionId() { return sessionId; }
  public String getAgentId() { return agentId; }
  public String getRuntimeExecutionId() { return runtimeExecutionId; }
  public RunStatus getStatus() { return status; }
  public String getErrorMessage() { return errorMessage; }
  public Instant getStartedAt() { return startedAt; }
  public Instant getFinishedAt() { return finishedAt; }
  public void setRuntimeExecutionId(String runtimeExecutionId) { this.runtimeExecutionId = runtimeExecutionId; }
}
