package com.schemaplatform.workflowchat.service;

import com.schemaplatform.workflowchat.domain.ChatMessage;
import com.schemaplatform.workflowchat.domain.ChatRun;
import com.schemaplatform.workflowchat.domain.MessageStatus;
import com.schemaplatform.workflowchat.domain.RunStatus;
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
   * <p>平台对 approved=false 会异步 cancelled；澄语立即本地收口并写引导，避免对话停住。
   */
  @Transactional
  public RunStatusView resume(String runId, String action, String payload) {
    ChatRun run = runService.getRun(runId);
    boolean rejected = isRejectAction(action);
    ExecutionStatusDto status = runtimeAdapter.resume(
        run.getRuntimeExecutionId(), run.getTenantId(),
        new RuntimeAdapter.ResumeRequest(action, payload));
    applyStatus(run, status);

    if (rejected) {
      // 平台取消常晚于 resume 响应；本地立即 CANCELLED + 引导，会话可继续发消息
      if (!run.isTerminal() || run.getStatus() != RunStatus.CANCELLED) {
        run.markCancelled();
      }
      appendHitlRejectFollowUp(run, payload);
    } else if (run.getStatus() == RunStatus.WAITING_INPUT) {
      // resume HTTP 已成功时，即使平台瞬时仍回 waiting，也推进为 RUNNING，避免重复 resume
      run.markRunning();
      updateAssistantMessage(run, null, null, MessageStatus.RUNNING);
    }

    ChatRun saved = runService.save(run);
    return RunStatusView.from(saved, status);
  }

  /**
   * 是否为拒绝类动作。
   * @param action 前端 action
   * @return true 表示拒绝 / 否认 / 取消确认
   */
  private static boolean isRejectAction(String action) {
    if (action == null || action.isBlank()) return false;
    String a = action.trim().toLowerCase();
    return a.equals("reject") || a.equals("deny") || a.equals("cancel");
  }

  /**
   * 拒绝确认后的会话续航：标注原气泡，并追加引导回复。
   * @param run 已取消的 run
   * @param payload 用户填写的补充/说明
   */
  private void appendHitlRejectFollowUp(ChatRun run, String payload) {
    ChatMessage placeholder = messageService.findAssistantByExecutionId(run.getRuntimeExecutionId());
    if (placeholder != null) {
      String existing = placeholder.getContent() == null ? "" : placeholder.getContent().trim();
      if (!existing.contains("本次确认已取消")) {
        String marked = existing.isEmpty()
            ? "**本次确认已取消。**"
            : existing + "\n\n---\n\n**本次确认已取消。**";
        messageService.updateAssistantResult(
            placeholder.getId(), marked, placeholder.getThinking(), MessageStatus.CANCELLED);
      } else {
        messageService.updateMessageStatus(placeholder.getId(), MessageStatus.CANCELLED);
      }
    }

    String revision = extractHumanRevision(payload);
    String followUp = revision.isEmpty()
        ? "好的，已取消本次确认。请直接告诉我你真正想做的事，我会重新开始。"
        : "好的，已取消上次确认。请确认或继续补充下面的需求，也可直接发送新的说明：\n\n> "
            + revision;
    // 避免重复 resume 写多条相同引导
    boolean alreadyGuided = messageService.listMessages(run.getSessionId()).stream()
        .anyMatch(m -> m.getRole() == ChatMessage.MessageRole.ASSISTANT
            && m.getContent() != null
            && m.getContent().contains("已取消本次确认")
            && m.getStatus() == MessageStatus.COMPLETED);
    if (!alreadyGuided) {
      messageService.saveAssistantResult(
          run.getSessionId(), followUp, null, MessageStatus.COMPLETED);
    }
    log.info("HITL 拒绝后续已写入 session={} run={} hasRevision={}",
        run.getSessionId(), run.getId(), !revision.isEmpty());
  }

  /**
   * 从 resume payload 提取可读的人工修正说明（忽略空 JSON）。
   * @param payload 前端 payload
   * @return 可读文本，可能为空
   */
  private static String extractHumanRevision(String payload) {
    if (payload == null) return "";
    String raw = payload.trim();
    if (raw.isEmpty()) return "";
    if (raw.startsWith("{") && raw.endsWith("}")) {
      try {
        com.fasterxml.jackson.databind.JsonNode node =
            new com.fasterxml.jackson.databind.ObjectMapper().readTree(raw);
        StringBuilder sb = new StringBuilder();
        node.fields().forEachRemaining(e -> {
          String v = e.getValue() == null || e.getValue().isNull()
              ? ""
              : e.getValue().asText("").trim();
          if (!v.isEmpty()) {
            if (sb.length() > 0) sb.append('；');
            sb.append(v);
          }
        });
        return sb.toString().trim();
      } catch (Exception ignored) {
        return raw;
      }
    }
    return raw;
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
        updateAssistantMessage(run, status.output(), status.thinking(), MessageStatus.COMPLETED,
            status.tip(), status.toolCallsJson(), status.documentSummariesJson(), status.workflowExecutionJson());
      }
      case FAILED -> {
        run.markFailed(status.errorMessage());
        updateAssistantMessage(run, status.errorMessage(), null, MessageStatus.FAILED,
            null, null, null, null);
      }
      case WAITING_INPUT -> {
        run.markWaiting();
        // 把需求分析/确认问题写入助手正文，前端气泡不再空白
        updateAssistantMessage(run, status.output(), status.thinking(), MessageStatus.WAITING_INPUT,
            status.tip(), status.toolCallsJson(), status.documentSummariesJson(), status.workflowExecutionJson());
      }
      case CANCELLED -> {
        run.markCancelled();
        updateAssistantMessage(run, null, null, MessageStatus.CANCELLED, null, null, null, null);
      }
      case RUNNING -> {
        run.markRunning();
        updateAssistantMessage(run, null, null, MessageStatus.RUNNING, null, null, null, null);
      }
      case UNKNOWN -> log.warn("Runtime 返回未知状态 run={}", run.getId());
    }
  }
      case FAILED -> {
        run.markFailed(status.errorMessage());
        updateAssistantMessage(run, status.errorMessage(), null, MessageStatus.FAILED);
      }
      case WAITING_INPUT -> {
        run.markWaiting();
        // 把需求分析/确认问题写入助手正文，前端气泡不再空白
        updateAssistantMessage(run, status.output(), status.thinking(), MessageStatus.WAITING_INPUT);
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

private void updateAssistantMessage(ChatRun run, String content, String thinking, MessageStatus status,
      String tip, String toolCallsJson, String documentSummariesJson, String workflowExecutionJson) {
    ChatMessage placeholder = messageService.findAssistantByExecutionId(run.getRuntimeExecutionId());
    if (placeholder != null) {
      if (content != null) {
        messageService.updateAssistantResult(placeholder.getId(), content, thinking, status);
      } else {
        messageService.updateMessageStatus(placeholder.getId(), status);
      }
      // 更新扩展字段
      messageService.updateMessageExtensions(placeholder.getId(), tip, toolCallsJson, documentSummariesJson, workflowExecutionJson);
    }
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