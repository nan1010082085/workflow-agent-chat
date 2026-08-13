package com.schemaplatform.workflowchat.controller;

import com.schemaplatform.workflowchat.domain.ChatMessage;
import com.schemaplatform.workflowchat.service.ChatService;
import com.schemaplatform.workflowchat.service.MessageService;
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

  public MessageController(MessageService messageService, ChatService chatService) {
    this.messageService = messageService;
    this.chatService = chatService;
  }

  @GetMapping("/{sessionId}/messages")
  public List<MessageDto> listMessages(@PathVariable String sessionId) {
    return messageService.listMessages(sessionId).stream()
        .map(MessageDto::from).toList();
  }

  @PostMapping("/{sessionId}/messages")
  public ChatService.SendMessageResult sendMessage(
      @PathVariable String sessionId,
      @RequestBody SendMessageRequest request) {
    return chatService.sendMessage(sessionId, request.agentId(), request.content());
  }

  @PostMapping("/{sessionId}/completions")
  public ChatService.ModelTurnResult completeModelTurn(
      @PathVariable String sessionId,
      @RequestBody ModelTurnRequest request) {
    return chatService.completeModelTurn(sessionId, request.modelId(), request.content());
  }

  public record SendMessageRequest(@NotBlank String agentId, @NotBlank String content) {}

  public record ModelTurnRequest(@NotBlank String modelId, @NotBlank String content) {}

  public record MessageDto(
      String id, String role, String content, String runtimeExecutionId,
      String status, Instant createdAt
  ) {
    static MessageDto from(ChatMessage m) {
      return new MessageDto(m.getId(), m.getRole().name().toLowerCase(),
          m.getContent(), m.getRuntimeExecutionId(), m.getStatus().name(), m.getCreatedAt());
    }
  }
}
