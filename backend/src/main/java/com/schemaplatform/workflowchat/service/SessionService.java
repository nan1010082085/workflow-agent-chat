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
  public List<ChatSession> listAllSessions(int page, int size) {
    String tenantId = TenantContext.tenantId();
    String userId = TenantContext.userId();
    org.springframework.data.domain.Pageable pageable = 
        org.springframework.data.domain.PageRequest.of(page, size, 
            org.springframework.data.domain.Sort.by("updatedAt").descending());
    return sessionRepository.findByTenantIdAndUserIdAndStatusOrderByUpdatedAtDesc(
        tenantId, userId, SessionStatus.ACTIVE, pageable).getContent();
  }

  @Transactional(readOnly = true)
  public long countSessions() {
    String tenantId = TenantContext.tenantId();
    String userId = TenantContext.userId();
    return sessionRepository.countByTenantIdAndUserIdAndStatus(tenantId, userId, SessionStatus.ACTIVE);
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
    session.updateTitle(normalizeTitle(title));
    return sessionRepository.save(session);
  }

  /**
   * 首条用户消息时，若标题仍是占位文案，则用消息内容生成会话标题。
   */
  @Transactional
  public ChatSession applyAutoTitleIfNeeded(String sessionId, String firstUserContent) {
    ChatSession session = getSession(sessionId);
    if (!isPlaceholderTitle(session.getTitle())) {
      return session;
    }
    session.updateTitle(titleFromContent(firstUserContent));
    return sessionRepository.save(session);
  }

  /** 从首条用户输入生成侧栏标题（单行、截断）。 */
  public static String titleFromContent(String content) {
    String text = content == null ? "" : content.replaceAll("\\s+", " ").trim();
    if (text.isEmpty()) return "新会话";
    if (text.length() > 40) return text.substring(0, 40) + "…";
    return text;
  }

  public static boolean isPlaceholderTitle(String title) {
    if (title == null || title.isBlank()) return true;
    if ("新会话".equals(title)) return true;
    return title.endsWith(" 会话");
  }

  private static String normalizeTitle(String title) {
    String text = title == null ? "" : title.replaceAll("\\s+", " ").trim();
    return text.isEmpty() ? "新会话" : (text.length() > 80 ? text.substring(0, 80) + "…" : text);
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