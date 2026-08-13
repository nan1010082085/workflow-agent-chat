package com.schemaplatform.workflowchat.controller;

import com.schemaplatform.workflowchat.domain.ChatMessage;
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
 * POST /api/chat/sessions/{id}/completions 基础模型对话并落库。
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

  public record MessageDto(
      String id,
      String role,
      String content,
      String thinking,
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
          m.getRuntimeExecutionId(),
          m.getStatus().name(),
          m.getCreatedAt(),
          attachments == null ? List.of() : attachments);
    }
  }
}
