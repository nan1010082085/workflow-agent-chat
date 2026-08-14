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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
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

  /** 传给平台的历史回合上限（与平台 trim 50 对齐，Chat 侧更保守） */
  private static final int WORKFLOW_HISTORY_MAX_TURNS = 20;
  /** 单条历史内容截断，避免 invoke 体过大 */
  private static final int WORKFLOW_HISTORY_MAX_CHARS = 2000;
  /** 无附件时，粘贴正文合成 paste.txt 的最短长度（对齐 document-parse 回退） */
  private static final int PASTE_AS_FILE_MIN_CHARS = 20;

  /**
   * 助手模式身份约束：专注本助手职责，不暴露基础平台品牌。
   */
  private static final String AGENT_FOCUS_HINT =
      "【澄语助手约束】你是当前已选工作流助手。请专注本助手职责与用户当前任务；"
          + "不要提及或介绍 schema-platform / 基础平台或底层实现；"
          + "若被问身份，以本助手名称与职责回答。\n\n";

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
    // 先取历史（不含本轮），供平台 workflow conversationHistory 初始化
    List<RuntimeAdapter.HistoryTurn> history = buildWorkflowHistory(sessionId);

    boolean firstMessage = messageService.countMessages(sessionId) == 0;
    ChatMessage userMsg = messageService.saveUserMessage(sessionId, normalized);
    List<ChatAttachment> attachments =
        uploadService.bindToMessage(userMsg.getId(), sessionId, attachmentIds);
    String runtimeInput = AGENT_FOCUS_HINT + normalized + UploadService.formatAttachmentContext(attachments);
    List<RuntimeAdapter.InvokeFile> invokeFiles = uploadService.toInvokeFiles(attachments);
    invokeFiles = withPasteFallbackFile(invokeFiles, normalized);

    if (firstMessage) {
      session = sessionService.applyAutoTitleIfNeeded(sessionId, normalized);
    }

    String idempotencyKey = UUID.randomUUID().toString();
    RuntimeAdapter.InvokeResult invokeResult;
    try {
      invokeResult = runtimeAdapter.invoke(new RuntimeAdapter.InvokeRequest(
          agent.slug(), runtimeInput, session.getTenantId(), session.getUserId(),
          sessionId, idempotencyKey, history, invokeFiles));
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
   * 基础模型对话（同步兜底）：落库 user/assistant，注入已发布助手目录。
   * 主路径为前端经平台 Socket.IO 流式后调用 {@link #persistStreamedModelTurn}。
   */
  @Transactional
  public ModelTurnResult completeModelTurn(
      String sessionId, String modelId, String content, List<String> attachmentIds) {
    ChatSession session = requireModelSession(sessionId);

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
    llmMessages.add(new ModelAdapter.Message("system", buildAgentAwarenessPrompt(modelId)));
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
        userMsg.getId(), assistant.getId(), reply, thinking, status,
        session.getTitle(), session.getPlatformConversationId());
  }

  /**
   * 落库前端经平台 WS 流式得到的模型回合（不调 LLM）。
   */
  @Transactional
  public ModelTurnResult persistStreamedModelTurn(
      String sessionId,
      String modelId,
      String content,
      List<String> attachmentIds,
      String assistantContent,
      String thinking,
      String platformConversationId,
      MessageStatus status) {
    ChatSession session = requireModelSession(sessionId);
    String normalized = normalizeContent(content, attachmentIds);
    boolean firstMessage = messageService.countMessages(sessionId) == 0;
    ChatMessage userMsg = messageService.saveUserMessage(sessionId, normalized);
    uploadService.bindToMessage(userMsg.getId(), sessionId, attachmentIds);

    if (firstMessage) {
      session = sessionService.applyAutoTitleIfNeeded(sessionId, normalized);
    }

    String reply = assistantContent == null || assistantContent.isBlank()
        ? (status == MessageStatus.FAILED ? "这次没有得到回复，请稍后重试。" : "（模型返回了空内容）")
        : assistantContent;
    MessageStatus finalStatus = status == null ? MessageStatus.COMPLETED : status;
    ChatMessage assistant = messageService.saveAssistantResult(sessionId, reply, thinking, finalStatus);

    if (platformConversationId != null && !platformConversationId.isBlank()) {
      session.setPlatformConversationId(platformConversationId);
      sessionService.save(session);
    }
    sessionService.touch(sessionId);
    session = sessionService.getSession(sessionId);

    log.info("WS 流式模型回合落库 session={} model={} platformConvo={} status={}",
        sessionId, modelId, session.getPlatformConversationId(), finalStatus);

    return new ModelTurnResult(
        userMsg.getId(), assistant.getId(), reply, thinking, finalStatus,
        session.getTitle(), session.getPlatformConversationId());
  }

  private ChatSession requireModelSession(String sessionId) {
    ChatSession session = sessionService.getSession(sessionId);
    if (session.getAgentId() != null && !session.getAgentId().isBlank()) {
      throw new IllegalStateException(
          "该对话已绑定助手「" + session.getAgentNameSnapshot() + "」，请新建对话使用基础模型");
    }
    return session;
  }

  /**
   * 注入澄语产品与已发布助手目录，避免模型否认「智能体」存在，并约束对外身份。
   * @param modelId 当前选用模型（可空）
   */
  private String buildAgentAwarenessPrompt(String modelId) {
    StringBuilder sb = new StringBuilder();
    sb.append("你正在「澄语」对话产品中。用户可选用基础模型，或切换到已发布的工作流助手。\n");
    sb.append("【身份规则】对外身份是「澄语」助手；不要自称 schema-platform、基础平台或其他底层平台品牌；");
    sb.append("不要解释底层实现或平台架构。\n");
    sb.append("若用户询问「你是谁」，回答你是澄语助手即可。\n");
    if (modelId != null && !modelId.isBlank()) {
      sb.append("若用户询问所用模型，可告知当前选用模型为「").append(modelId.trim()).append("」。\n");
    } else {
      sb.append("若用户询问所用模型且未指定具体模型，请如实说明模型名称未知，不要编造平台品牌。\n");
    }
    sb.append("若用户询问有哪些智能体/助手/Agent，请根据下列已发布列表介绍，");
    sb.append("并引导其在界面中选择对应助手开始对话；不要声称系统没有智能体或无法获知列表；不要扯平台实现。\n");
    try {
      List<AgentDto> agents = agentCatalogService.listAgents().stream()
          .filter(AgentDto::published)
          .toList();
      if (agents.isEmpty()) {
        sb.append("当前租户暂无已发布助手；可说明这一点并建议稍后再试或联系管理员发布。");
      } else {
        sb.append("【当前租户已发布助手】\n");
        int i = 1;
        for (AgentDto a : agents) {
          sb.append(i++).append(". ").append(a.name());
          if (a.slug() != null && !a.slug().isBlank()) {
            sb.append("（").append(a.slug()).append('）');
          }
          if (a.description() != null && !a.description().isBlank()) {
            sb.append(" — ").append(a.description().trim());
          }
          sb.append('\n');
        }
      }
    } catch (Exception e) {
      log.warn("加载助手目录失败，跳过注入: {}", e.getMessage());
      sb.append("助手列表暂时不可用；可建议用户稍后在界面刷新助手列表。");
    }
    return sb.toString();
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

  /**
   * 无真实附件时，把足够长的粘贴正文合成 text/plain，写入平台 $input.file。
   * 与平台 document-parse 的 message 回退互补，覆盖粘贴合同/纪要场景。
   * @param files 已有附件
   * @param content 本轮用户正文
   * @return 可能追加了 paste.txt 的列表
   */
  private static List<RuntimeAdapter.InvokeFile> withPasteFallbackFile(
      List<RuntimeAdapter.InvokeFile> files, String content) {
    if (files != null && !files.isEmpty()) {
      return files;
    }
    String text = content == null ? "" : content.trim();
    if (text.length() < PASTE_AS_FILE_MIN_CHARS) {
      return files == null ? List.of() : files;
    }
    String b64 = Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    return List.of(new RuntimeAdapter.InvokeFile("paste.txt", "text/plain", b64));
  }

  /**
   * 从澄语会话组装平台 workflow 多轮历史（不含本轮即将发送的用户消息）。
   * 只取已完成的 user/assistant 正文，过滤失败占位与空内容。
   */
  private List<RuntimeAdapter.HistoryTurn> buildWorkflowHistory(String sessionId) {
    List<ChatMessage> all = messageService.listMessages(sessionId);
    List<RuntimeAdapter.HistoryTurn> turns = new ArrayList<>();
    for (ChatMessage m : all) {
      if (m.getContent() == null || m.getContent().isBlank()) continue;
      if (m.getRole() == ChatMessage.MessageRole.USER) {
        turns.add(new RuntimeAdapter.HistoryTurn("user", truncateHistory(m.getContent())));
      } else if (m.getRole() == ChatMessage.MessageRole.ASSISTANT) {
        if (m.getStatus() != null && m.getStatus() != MessageStatus.COMPLETED) continue;
        turns.add(new RuntimeAdapter.HistoryTurn("assistant", truncateHistory(m.getContent())));
      }
    }
    if (turns.size() <= WORKFLOW_HISTORY_MAX_TURNS) {
      return turns;
    }
    return turns.subList(turns.size() - WORKFLOW_HISTORY_MAX_TURNS, turns.size());
  }

  private static String truncateHistory(String content) {
    String text = content.trim();
    if (text.length() <= WORKFLOW_HISTORY_MAX_CHARS) return text;
    return text.substring(0, WORKFLOW_HISTORY_MAX_CHARS) + "…";
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
      String sessionTitle,
      String platformConversationId
  ) {}
}
