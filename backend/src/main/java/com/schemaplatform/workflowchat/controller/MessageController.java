package com.schemaplatform.workflowchat.controller;

import com.schemaplatform.workflowchat.domain.ChatMessage;
import com.schemaplatform.workflowchat.domain.MessageStatus;
import com.schemaplatform.workflowchat.service.ChatService;
import com.schemaplatform.workflowchat.service.MessageService;
import com.schemaplatform.workflowchat.service.UploadService;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 消息接口。对应 ARCHITECTURE §3 的 message 相关 API。
 * POST /api/chat/sessions/{id}/messages 发送消息并触发 Runtime 执行。
 * POST /api/chat/sessions/{id}/completions 基础模型同步对话并落库（兜底）。
 * POST /api/chat/sessions/{id}/model-turns 落库前端经平台 WS 流式得到的模型回合。
 */
@RestController
@RequestMapping("/api/chat/sessions")
public class MessageController {

  private final MessageService messageService;
  private final ChatService chatService;
  private final UploadService uploadService;

  public MessageController(MessageService messageService, ChatService chatService,
      UploadService uploadService) {
    this.messageService = messageService;
    this.chatService = chatService;
    this.uploadService = uploadService;
  }

  @GetMapping("/{sessionId}/messages")
  public List<MessageDto> listMessages(@PathVariable String sessionId) {
    return messageService.listMessages(sessionId).stream()
        .map(this::toDto).toList();
  }

  @PostMapping("/{sessionId}/messages")
  public ChatService.SendMessageResult sendMessage(
      @PathVariable String sessionId,
      @RequestBody SendMessageRequest request) {
    return chatService.sendMessage(
        sessionId, request.agentId(), request.content(), request.attachmentIds());
  }

  @PostMapping("/{sessionId}/completions")
  public ChatService.ModelTurnResult completeModelTurn(
      @PathVariable String sessionId,
      @RequestBody ModelTurnRequest request) {
    return chatService.completeModelTurn(
        sessionId, request.modelId(), request.content(), request.attachmentIds());
  }

  /**
   * 持久化平台 WS 流式回合（正文 + thinking），不调用 LLM。
   */
  @PostMapping("/{sessionId}/model-turns")
  public ChatService.ModelTurnResult persistModelTurn(
      @PathVariable String sessionId,
      @RequestBody PersistModelTurnRequest request) {
    MessageStatus status = parseStatus(request.status());
    return chatService.persistStreamedModelTurn(
        sessionId,
        request.modelId(),
        request.content(),
        request.attachmentIds(),
        request.assistantContent(),
        request.thinking(),
        request.platformConversationId(),
        status);
  }

  private static MessageStatus parseStatus(String raw) {
    if (raw == null || raw.isBlank()) return MessageStatus.COMPLETED;
    try {
      return MessageStatus.valueOf(raw.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      return MessageStatus.COMPLETED;
    }
  }

  private MessageDto toDto(ChatMessage m) {
    List<UploadController.AttachmentDto> attachments = uploadService.listByMessage(m.getId())
        .stream()
        .map(UploadController.AttachmentDto::from)
        .toList();
    return MessageDto.from(m, attachments);
  }

  public record SendMessageRequest(
      @NotBlank String agentId,
      String content,
      List<String> attachmentIds
  ) {}

  public record ModelTurnRequest(
      @NotBlank String modelId,
      String content,
      List<String> attachmentIds
  ) {}

  public record PersistModelTurnRequest(
      @NotBlank String modelId,
      String content,
      List<String> attachmentIds,
      String assistantContent,
      String thinking,
      String platformConversationId,
      String status
  ) {}

  public record MessageDto(
      String id,
      String role,
      String content,
      String thinking,
      String tip,
      String toolCalls,
      String documentSummaries,
      String workflowExecution,
      String runtimeExecutionId,
      String status,
      Instant createdAt,
      List<UploadController.AttachmentDto> attachments
  ) {
    static MessageDto from(ChatMessage m, List<UploadController.AttachmentDto> attachments) {
      return new MessageDto(
          m.getId(),
          m.getRole().name().toLowerCase(),
          m.getContent(),
          m.getThinking(),
          m.getTip(),
          m.getToolCallsJson(),
          m.getDocumentSummariesJson(),
          m.getWorkflowExecutionJson(),
          m.getRuntimeExecutionId(),
          m.getStatus().name(),
          m.getCreatedAt(),
          attachments == null ? List.of() : attachments);
    }
  }
}
