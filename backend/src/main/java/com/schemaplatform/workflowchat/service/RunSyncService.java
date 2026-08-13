package com.schemaplatform.workflowchat.service;

import com.schemaplatform.workflowchat.domain.ChatMessage;
import com.schemaplatform.workflowchat.domain.ChatRun;
import com.schemaplatform.workflowchat.domain.MessageStatus;
import com.schemaplatform.workflowchat.runtime.ExecutionStatusDto;
import com.schemaplatform.workflowchat.runtime.RuntimeAdapter;
import com.schemaplatform.workflowchat.runtime.RuntimeUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Run 状态同步服务。对应 TASKS B-07。
 *
 * <p>从 Runtime 拉取执行状态，映射到 ChatRun + assistant message。
 * 幂等：通过 runtimeExecutionId 找到已有 placeholder 更新，不新建消息（B-07 验收）。
 * 刷新后可恢复：前端刷新后再次查询，终态保持一致。
 */
@Service
public class RunSyncService {

  private static final Logger log = LoggerFactory.getLogger(RunSyncService.class);

  private final RunService runService;
  private final MessageService messageService;
  private final RuntimeAdapter runtimeAdapter;

  public RunSyncService(RunService runService, MessageService messageService,
      RuntimeAdapter runtimeAdapter) {
    this.runService = runService;
    this.messageService = messageService;
    this.runtimeAdapter = runtimeAdapter;
  }

  /**
   * 同步指定 run 的状态。用于前端轮询 GET /api/chat/runs/{id}。
   * 返回最新状态 DTO，供 controller 输出。
   */
  @Transactional
  public RunStatusView syncRun(String runId) {
    ChatRun run = runService.getRun(runId);
    if (run.isTerminal()) {
      // 终态不再查询 Runtime，直接返回缓存
      return RunStatusView.from(run, null);
    }
    ExecutionStatusDto status;
    try {
      status = runtimeAdapter.getExecutionStatus(run.getRuntimeExecutionId(), run.getTenantId());
    } catch (RuntimeUnavailableException e) {
      log.warn("同步 run={} 时 Runtime 不可用: {}", runId, e.getMessage());
      return RunStatusView.from(run, null);
    }
    if (status == null) {
      return RunStatusView.from(run, null);
    }
    applyStatus(run, status);
    return RunStatusView.from(runService.save(run), status);
  }

  /**
   * 按 Runtime executionId 反查并同步（刷新恢复 waiting / running）。
   */
  @Transactional
  public RunStatusView syncByExecutionId(String runtimeExecutionId) {
    ChatRun run = runService.findByExecutionId(runtimeExecutionId);
    if (run == null) {
      throw new java.util.NoSuchElementException("运行不存在或无权访问: " + runtimeExecutionId);
    }
    return syncRun(run.getId());
  }

  /**
   * HITL resume。对应 TASKS B-08。
   */
  @Transactional
  public RunStatusView resume(String runId, String action, String payload) {
    ChatRun run = runService.getRun(runId);
    ExecutionStatusDto status = runtimeAdapter.resume(
        run.getRuntimeExecutionId(), run.getTenantId(),
        new RuntimeAdapter.ResumeRequest(action, payload));
    applyStatus(run, status);
    ChatRun saved = runService.save(run);
    return RunStatusView.from(saved, status);
  }

  /**
   * cancel。对应 TASKS B-09。
   */
  @Transactional
  public RunStatusView cancel(String runId) {
    ChatRun run = runService.getRun(runId);
    if (run.isTerminal()) {
      throw new IllegalStateException("运行已结束，无法取消: " + runId);
    }
    ExecutionStatusDto status = runtimeAdapter.cancel(
        run.getRuntimeExecutionId(), run.getTenantId());
    if (status != null) {
      applyStatus(run, status);
    } else {
      run.markCancelled();
    }
    ChatRun saved = runService.save(run);
    return RunStatusView.from(saved, status);
  }

  private void applyStatus(ChatRun run, ExecutionStatusDto status) {
    switch (status.status()) {
      case COMPLETED -> {
        run.markCompleted();
        updateAssistantMessage(run, status.output(), status.thinking(), MessageStatus.COMPLETED);
      }
      case FAILED -> {
        run.markFailed(status.errorMessage());
        updateAssistantMessage(run, status.errorMessage(), null, MessageStatus.FAILED);
      }
      case WAITING_INPUT -> {
        run.markWaiting();
        updateAssistantMessage(run, null, null, MessageStatus.WAITING_INPUT);
      }
      case CANCELLED -> {
        run.markCancelled();
        updateAssistantMessage(run, null, null, MessageStatus.CANCELLED);
      }
      case RUNNING -> {
        run.markRunning();
        updateAssistantMessage(run, null, null, MessageStatus.RUNNING);
      }
      case UNKNOWN -> log.warn("Runtime 返回未知状态 run={}", run.getId());
    }
  }

  private void updateAssistantMessage(ChatRun run, String content, String thinking, MessageStatus status) {
    ChatMessage placeholder = messageService.findAssistantByExecutionId(run.getRuntimeExecutionId());
    if (placeholder != null) {
      if (content != null) {
        messageService.updateAssistantResult(placeholder.getId(), content, thinking, status);
      } else {
        messageService.updateMessageStatus(placeholder.getId(), status);
      }
    }
  }

  /**
   * 给前端的 run 状态视图。包含 waiting 载荷供 ApprovalCard 渲染。
   */
  public record RunStatusView(
      String runId,
      String sessionId,
      String agentId,
      String runtimeExecutionId,
      String status,
      String errorMessage,
      ExecutionStatusDto.WaitingPayloadDto waiting,
      java.time.Instant startedAt,
      java.time.Instant finishedAt
  ) {
    static RunStatusView from(ChatRun run, ExecutionStatusDto status) {
      ExecutionStatusDto.WaitingPayloadDto waiting = null;
      if (status != null && status.status() == ExecutionStatusDto.RunStatusDto.WAITING_INPUT) {
        waiting = status.waiting();
      }
      return new RunStatusView(
          run.getId(), run.getSessionId(), run.getAgentId(),
          run.getRuntimeExecutionId(), run.getStatus().name(),
          run.getErrorMessage(), waiting,
          run.getStartedAt(), run.getFinishedAt());
    }
  }
}
