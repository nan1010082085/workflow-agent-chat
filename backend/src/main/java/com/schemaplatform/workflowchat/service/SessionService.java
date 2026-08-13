package com.schemaplatform.workflowchat.service;

import com.schemaplatform.workflowchat.domain.ChatSession;
import com.schemaplatform.workflowchat.domain.SessionStatus;
import com.schemaplatform.workflowchat.repository.ChatSessionRepository;
import com.schemaplatform.workflowchat.tenant.TenantContext;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 会话服务。所有操作带租户与用户隔离。
 */
@Service
public class SessionService {

  private final ChatSessionRepository sessionRepository;

  public SessionService(ChatSessionRepository sessionRepository) {
    this.sessionRepository = sessionRepository;
  }

  @Transactional
  public ChatSession createSession(String title, String agentId, String agentNameSnapshot) {
    String tenantId = TenantContext.tenantId();
    String userId = TenantContext.userId();
    ChatSession session = ChatSession.create(
        UUID.randomUUID().toString(), tenantId, userId, title, agentId, agentNameSnapshot);
    return sessionRepository.save(session);
  }

  @Transactional(readOnly = true)
  public List<ChatSession> listRecentSessions() {
    String tenantId = TenantContext.tenantId();
    String userId = TenantContext.userId();
    return sessionRepository.findTop20ByTenantIdAndUserIdAndStatusOrderByUpdatedAtDesc(
        tenantId, userId, SessionStatus.ACTIVE);
  }

  @Transactional(readOnly = true)
  public ChatSession getSession(String sessionId) {
    String tenantId = TenantContext.tenantId();
    String userId = TenantContext.userId();
    return sessionRepository.findById(sessionId)
        .filter(s -> s.belongsTo(tenantId, userId))
        .orElseThrow(() -> new NoSuchElementException("会话不存在或无权访问: " + sessionId));
  }

  @Transactional
  public ChatSession updateTitle(String sessionId, String title) {
    ChatSession session = getSession(sessionId);
    session.updateTitle(title);
    return sessionRepository.save(session);
  }

  @Transactional
  public void archive(String sessionId) {
    ChatSession session = getSession(sessionId);
    session.archive();
    sessionRepository.save(session);
  }

  @Transactional
  public ChatSession touch(String sessionId) {
    ChatSession session = getSession(sessionId);
    session.touch();
    return sessionRepository.save(session);
  }

  @Transactional
  public ChatSession save(ChatSession session) {
    return sessionRepository.save(session);
  }
}
