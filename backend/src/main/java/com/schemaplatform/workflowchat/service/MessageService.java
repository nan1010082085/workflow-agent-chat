package com.schemaplatform.workflowchat.service;

import com.schemaplatform.workflowchat.domain.ChatMessage;
import com.schemaplatform.workflowchat.domain.MessageStatus;
import com.schemaplatform.workflowchat.repository.ChatMessageRepository;
import com.schemaplatform.workflowchat.tenant.TenantContext;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 消息服务。保存与查询消息，带租户隔离。
 */
@Service
public class MessageService {

  private final ChatMessageRepository messageRepository;

  public MessageService(ChatMessageRepository messageRepository) {
    this.messageRepository = messageRepository;
  }

  @Transactional
  public ChatMessage saveUserMessage(String sessionId, String content) {
    String tenantId = TenantContext.tenantId();
    return messageRepository.save(
        ChatMessage.userMessage(UUID.randomUUID().toString(), tenantId, sessionId, content));
  }

  @Transactional
  public ChatMessage saveAssistantPlaceholder(String sessionId, String runtimeExecutionId) {
    String tenantId = TenantContext.tenantId();
    return messageRepository.save(
        ChatMessage.assistantPlaceholder(UUID.randomUUID().toString(), tenantId, sessionId, runtimeExecutionId));
  }

  /** 保存已完成的助手回复（基础模型对话用）。 */
  @Transactional
  public ChatMessage saveAssistantResult(String sessionId, String content, String thinking, MessageStatus status) {
    String tenantId = TenantContext.tenantId();
    return messageRepository.save(
        ChatMessage.assistantResult(
            UUID.randomUUID().toString(), tenantId, sessionId, content, thinking, null, status));
  }

  @Transactional(readOnly = true)
  public long countMessages(String sessionId) {
    String tenantId = TenantContext.tenantId();
    return messageRepository.countByTenantIdAndSessionId(tenantId, sessionId);
  }

  @Transactional(readOnly = true)
  public List<ChatMessage> listMessages(String sessionId) {
    String tenantId = TenantContext.tenantId();
    return messageRepository.findByTenantIdAndSessionIdOrderByCreatedAtAsc(tenantId, sessionId);
  }

  /**
   * 根据 runtimeExecutionId 找到对应的 assistant placeholder，用于轮询回填结果。
   */
  @Transactional(readOnly = true)
  public ChatMessage findAssistantByExecutionId(String runtimeExecutionId) {
    String tenantId = TenantContext.tenantId();
    return messageRepository
        .findByTenantIdAndRuntimeExecutionIdAndRole(tenantId, runtimeExecutionId, ChatMessage.MessageRole.ASSISTANT)
        .orElse(null);
  }

  @Transactional
  public ChatMessage updateAssistantResult(String messageId, String content, String thinking, MessageStatus status) {
    ChatMessage msg = messageRepository.findById(messageId)
        .orElseThrow(() -> new IllegalStateException("消息不存在: " + messageId));
    msg.updateResult(content, thinking, status);
    return messageRepository.save(msg);
  }

  @Transactional
  public ChatMessage updateMessageStatus(String messageId, MessageStatus status) {
    ChatMessage msg = messageRepository.findById(messageId)
        .orElseThrow(() -> new IllegalStateException("消息不存在: " + messageId));
    msg.updateStatus(status);
    return messageRepository.save(msg);
  }
}
