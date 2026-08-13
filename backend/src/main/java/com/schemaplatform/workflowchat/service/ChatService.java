package com.schemaplatform.workflowchat.service;

import com.schemaplatform.workflowchat.domain.ChatAttachment;
import com.schemaplatform.workflowchat.domain.ChatMessage;
import com.schemaplatform.workflowchat.domain.ChatRun;
import com.schemaplatform.workflowchat.domain.ChatSession;
import com.schemaplatform.workflowchat.domain.MessageStatus;
import com.schemaplatform.workflowchat.runtime.AgentDto;
import com.schemaplatform.workflowchat.runtime.ExecutionStatusDto;
import com.schemaplatform.workflowchat.runtime.ModelAdapter;
import com.schemaplatform.workflowchat.runtime.RuntimeAdapter;
import com.schemaplatform.workflowchat.tenant.TenantContext;
import java.util.ArrayList;
import java.util.List;
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
  private final ModelAdapter modelAdapter;
  private final UploadService uploadService;

  public ChatService(SessionService sessionService, MessageService messageService,
      RunService runService, AgentCatalogService agentCatalogService,
      RuntimeAdapter runtimeAdapter, ModelAdapter modelAdapter, UploadService uploadService) {
    this.sessionService = sessionService;
    this.messageService = messageService;
    this.runService = runService;
    this.agentCatalogService = agentCatalogService;
    this.runtimeAdapter = runtimeAdapter;
    this.modelAdapter = modelAdapter;
    this.uploadService = uploadService;
  }

  /**
   * 发送消息。对应 TASKS B-01：session 绑定固定助手，不允许跨助手续聊。
   * 返回 messageId/runId/status 给前端立即渲染。
   */
  @Transactional
  public SendMessageResult sendMessage(
      String sessionId, String agentId, String content, List<String> attachmentIds) {
    ChatSession session = sessionService.getSession(sessionId);
    AgentDto agent = agentCatalogService.getAgent(agentId);

    if (session.getAgentId() == null || session.getAgentId().isBlank()) {
      session.setAgentId(agent.id());
      session.setAgentNameSnapshot(agent.name());
    } else if (!session.getAgentId().equals(agent.id())) {
      throw new IllegalStateException(
          "该对话已绑定助手「" + session.getAgentNameSnapshot() + "」，请创建新对话使用其他助手");
    }

    if (runService.hasActiveRun(sessionId)) {
      throw new IllegalStateException("当前对话有任务正在处理，请等待完成后再发送");
    }
    sessionService.save(session);

    String normalized = normalizeContent(content, attachmentIds);
    boolean firstMessage = messageService.countMessages(sessionId) == 0;
    ChatMessage userMsg = messageService.saveUserMessage(sessionId, normalized);
    List<ChatAttachment> attachments =
        uploadService.bindToMessage(userMsg.getId(), sessionId, attachmentIds);
    String runtimeInput = normalized + UploadService.formatAttachmentContext(attachments);

    if (firstMessage) {
      session = sessionService.applyAutoTitleIfNeeded(sessionId, normalized);
    }

    String idempotencyKey = UUID.randomUUID().toString();
    RuntimeAdapter.InvokeResult invokeResult;
    try {
      invokeResult = runtimeAdapter.invoke(new RuntimeAdapter.InvokeRequest(
          agent.slug(), runtimeInput, session.getTenantId(), session.getUserId(),
          sessionId, idempotencyKey));
    } catch (Exception e) {
      throw e;
    }

    ChatRun run = runService.startRun(sessionId, agentId, invokeResult.runtimeExecutionId());
    ChatMessage placeholder = messageService.saveAssistantPlaceholder(sessionId, invokeResult.runtimeExecutionId());

    MessageStatus initialMsgStatus = mapToMessageStatus(invokeResult.initialStatus());
    if (initialMsgStatus != MessageStatus.RUNNING) {
      messageService.updateMessageStatus(placeholder.getId(), initialMsgStatus);
      if (invokeResult.initialStatus() == ExecutionStatusDto.RunStatusDto.WAITING_INPUT) {
        run.markWaiting();
        runService.save(run);
      }
    }

    sessionService.touch(sessionId);

    log.info("消息发送闭环完成 session={} agent={} run={} execution={} status={} attachments={}",
        sessionId, agentId, run.getId(), invokeResult.runtimeExecutionId(),
        invokeResult.initialStatus(), attachments.size());

    return new SendMessageResult(userMsg.getId(), placeholder.getId(), run.getId(),
        invokeResult.runtimeExecutionId(), initialMsgStatus, session.getTitle());
  }

  /**
   * 基础模型对话：落库 user/assistant，并在首条消息时自动生成标题。
   */
  @Transactional
  public ModelTurnResult completeModelTurn(
      String sessionId, String modelId, String content, List<String> attachmentIds) {
    ChatSession session = sessionService.getSession(sessionId);
    if (session.getAgentId() != null && !session.getAgentId().isBlank()) {
      throw new IllegalStateException(
          "该对话已绑定助手「" + session.getAgentNameSnapshot() + "」，请新建对话使用基础模型");
    }

    String normalized = normalizeContent(content, attachmentIds);
    List<ChatMessage> history = messageService.listMessages(sessionId);
    boolean firstMessage = history.isEmpty();
    ChatMessage userMsg = messageService.saveUserMessage(sessionId, normalized);
    List<ChatAttachment> attachments =
        uploadService.bindToMessage(userMsg.getId(), sessionId, attachmentIds);
    String userPayload = normalized + UploadService.formatAttachmentContext(attachments);

    if (firstMessage) {
      session = sessionService.applyAutoTitleIfNeeded(sessionId, normalized);
    }

    List<ModelAdapter.Message> llmMessages = new ArrayList<>();
    for (ChatMessage m : history) {
      if (m.getContent() == null || m.getContent().isBlank()) continue;
      String hist = m.getContent();
      if (m.getRole() == ChatMessage.MessageRole.USER) {
        List<ChatAttachment> histAtt = uploadService.listByMessage(m.getId());
        hist = hist + UploadService.formatAttachmentContext(histAtt);
      }
      llmMessages.add(new ModelAdapter.Message(m.getRole().name().toLowerCase(), hist));
    }
    llmMessages.add(new ModelAdapter.Message("user", userPayload));

    String reply;
    String thinking = null;
    MessageStatus status = MessageStatus.COMPLETED;
    try {
      ModelAdapter.CompletionResult result =
          modelAdapter.complete(TenantContext.tenantId(), modelId, llmMessages);
      reply = result.content();
      thinking = result.thinking();
      if (reply == null || reply.isBlank()) {
        reply = "（模型返回了空内容）";
      }
    } catch (Exception e) {
      log.warn("模型补全失败 session={} model={}: {}", sessionId, modelId, e.getMessage());
      reply = "这次没有得到回复，请稍后重试。";
      status = MessageStatus.FAILED;
    }

    ChatMessage assistant = messageService.saveAssistantResult(sessionId, reply, thinking, status);
    sessionService.touch(sessionId);
    session = sessionService.getSession(sessionId);

    return new ModelTurnResult(
        userMsg.getId(), assistant.getId(), reply, thinking, status, session.getTitle());
  }

  private static String normalizeContent(String content, List<String> attachmentIds) {
    String text = content == null ? "" : content.trim();
    boolean hasFiles = attachmentIds != null && !attachmentIds.isEmpty();
    if (text.isEmpty() && !hasFiles) {
      throw new IllegalArgumentException("请输入消息或添加附件");
    }
    if (text.isEmpty()) {
      return "（见附件）";
    }
    return text;
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
      MessageStatus status,
      String sessionTitle
  ) {}

  public record ModelTurnResult(
      String messageId,
      String assistantMessageId,
      String content,
      String thinking,
      MessageStatus status,
      String sessionTitle
  ) {}
}
