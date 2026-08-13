package com.schemaplatform.workflowchat.service;

import com.schemaplatform.workflowchat.domain.ChatMessage;
import com.schemaplatform.workflowchat.domain.ChatRun;
import com.schemaplatform.workflowchat.domain.ChatSession;
import com.schemaplatform.workflowchat.domain.MessageStatus;
import com.schemaplatform.workflowchat.domain.RunStatus;
import com.schemaplatform.workflowchat.runtime.AgentDto;
import com.schemaplatform.workflowchat.runtime.ExecutionStatusDto;
import com.schemaplatform.workflowchat.runtime.RuntimeAdapter;
import com.schemaplatform.workflowchat.tenant.TenantContext;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 消息发送闭环协调器。对应 TASKS B-06。
 *
 * <p>流程：保存 user message -> 调 Runtime invoke -> 落 run + assistant placeholder
 * -> 返回 messageId/runId/status。原子性由 @Transactional 保证落库；
 * Runtime invoke 失败时回滚 user message，避免幽灵消息（PRD 验收标准 6）。
 */
@Service
public class ChatService {

  private static final Logger log = LoggerFactory.getLogger(ChatService.class);

  private final SessionService sessionService;
  private final MessageService messageService;
  private final RunService runService;
  private final AgentCatalogService agentCatalogService;
  private final RuntimeAdapter runtimeAdapter;

  public ChatService(SessionService sessionService, MessageService messageService,
      RunService runService, AgentCatalogService agentCatalogService,
      RuntimeAdapter runtimeAdapter) {
    this.sessionService = sessionService;
    this.messageService = messageService;
    this.runService = runService;
    this.agentCatalogService = agentCatalogService;
    this.runtimeAdapter = runtimeAdapter;
  }

  /**
   * 发送消息。对应 TASKS B-01：session 绑定固定助手，不允许跨助手续聊。
   * 返回 messageId/runId/status 给前端立即渲染。
   */
  @Transactional
  public SendMessageResult sendMessage(String sessionId, String agentId, String content) {
    ChatSession session = sessionService.getSession(sessionId);
    AgentDto agent = agentCatalogService.getAgent(agentId);

    // B-01：session-agent 绑定校验
    if (session.getAgentId() == null || session.getAgentId().isBlank()) {
      // 新会话首次发送：绑定助手并快照名称
      session.setAgentId(agent.id());
      session.setAgentNameSnapshot(agent.name());
    } else if (!session.getAgentId().equals(agent.id())) {
      // 已绑定助手，不允许跨助手续聊
      throw new IllegalStateException(
          "该对话已绑定助手「" + session.getAgentNameSnapshot() + "」，请创建新对话使用其他助手");
    }

    // B-06：幂等/串行校验——一个会话同时只允许一个进行中的任务
    if (runService.hasActiveRun(sessionId)) {
      throw new IllegalStateException("当前对话有任务正在处理，请等待完成后再发送");
    }
    sessionService.save(session);

    // 1. 落 user message
    ChatMessage userMsg = messageService.saveUserMessage(sessionId, content);

    // 2. 调 Runtime invoke
    String idempotencyKey = UUID.randomUUID().toString();
    RuntimeAdapter.InvokeResult invokeResult;
    try {
      invokeResult = runtimeAdapter.invoke(new RuntimeAdapter.InvokeRequest(
          agent.slug(), content, session.getTenantId(), session.getUserId(),
          sessionId, idempotencyKey));
    } catch (Exception e) {
      // invoke 失败，事务回滚 user message，不产生幽灵消息
      throw e;
    }

    // 3. 落 run + assistant placeholder
    ChatRun run = runService.startRun(sessionId, agentId, invokeResult.runtimeExecutionId());
    ChatMessage placeholder = messageService.saveAssistantPlaceholder(sessionId, invokeResult.runtimeExecutionId());

    // 4. 同步初始状态（HITL 可能立即 waiting）
    MessageStatus initialMsgStatus = mapToMessageStatus(invokeResult.initialStatus());
    if (initialMsgStatus != MessageStatus.RUNNING) {
      messageService.updateMessageStatus(placeholder.getId(), initialMsgStatus);
      if (invokeResult.initialStatus() == ExecutionStatusDto.RunStatusDto.WAITING_INPUT) {
        run.markWaiting();
        runService.save(run);
      }
    }

    // 5. touch session
    sessionService.touch(sessionId);

    log.info("消息发送闭环完成 session={} agent={} run={} execution={} status={}",
        sessionId, agentId, run.getId(), invokeResult.runtimeExecutionId(), invokeResult.initialStatus());

    return new SendMessageResult(userMsg.getId(), placeholder.getId(), run.getId(),
        invokeResult.runtimeExecutionId(), initialMsgStatus);
  }

  private MessageStatus mapToMessageStatus(ExecutionStatusDto.RunStatusDto runtimeStatus) {
    if (runtimeStatus == null) return MessageStatus.RUNNING;
    return switch (runtimeStatus) {
      case RUNNING -> MessageStatus.RUNNING;
      case COMPLETED -> MessageStatus.COMPLETED;
      case FAILED -> MessageStatus.FAILED;
      case WAITING_INPUT -> MessageStatus.WAITING_INPUT;
      case CANCELLED -> MessageStatus.CANCELLED;
      case UNKNOWN -> MessageStatus.RUNNING;
    };
  }

  public record SendMessageResult(
      String messageId,
      String assistantMessageId,
      String runId,
      String runtimeExecutionId,
      MessageStatus status
  ) {}
}
