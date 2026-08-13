package com.schemaplatform.workflowchat.controller;

import com.schemaplatform.workflowchat.domain.ChatSession;
import com.schemaplatform.workflowchat.service.SessionService;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会话接口。对应 ARCHITECTURE §3 的 session 相关 API。
 */
@RestController
@RequestMapping("/api/chat/sessions")
public class SessionController {

  private final SessionService sessionService;

  public SessionController(SessionService sessionService) {
    this.sessionService = sessionService;
  }

  @GetMapping
  public List<SessionSummary> listSessions() {
    return sessionService.listRecentSessions().stream()
        .map(SessionSummary::from).toList();
  }

  @PostMapping
  public SessionSummary createSession(@RequestBody CreateSessionRequest request) {
    String title = request.title() != null && !request.title().isBlank()
        ? request.title()
        : (request.agentName() != null ? request.agentName() + " 会话" : "新会话");
    ChatSession session = sessionService.createSession(title, request.agentId(), request.agentName());
    return SessionSummary.from(session);
  }

  @PatchMapping("/{sessionId}/title")
  public SessionSummary updateTitle(
      @PathVariable String sessionId,
      @RequestBody UpdateTitleRequest request) {
    return SessionSummary.from(sessionService.updateTitle(sessionId, request.title()));
  }

  public record CreateSessionRequest(String title, String agentId, String agentName) {}

  public record UpdateTitleRequest(String title) {}

  public record SessionSummary(
      String id, String title, String agentId, String agentName,
      String status, Instant createdAt, Instant updatedAt
  ) {
    static SessionSummary from(ChatSession s) {
      return new SessionSummary(s.getId(), s.getTitle(), s.getAgentId(),
          s.getAgentNameSnapshot(), s.getStatus().name(), s.getCreatedAt(), s.getUpdatedAt());
    }
  }
}
