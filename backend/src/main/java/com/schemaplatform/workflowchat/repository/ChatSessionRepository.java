package com.schemaplatform.workflowchat.repository;

import com.schemaplatform.workflowchat.domain.ChatSession;
import com.schemaplatform.workflowchat.domain.SessionStatus;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatSessionRepository extends JpaRepository<ChatSession, String> {

  Page<ChatSession> findByTenantIdAndUserIdAndStatusOrderByUpdatedAtDesc(
      String tenantId, String userId, SessionStatus status, Pageable pageable);

  List<ChatSession> findTop20ByTenantIdAndUserIdAndStatusOrderByUpdatedAtDesc(
      String tenantId, String userId, SessionStatus status);

  long countByTenantIdAndUserIdAndStatus(String tenantId, String userId, SessionStatus status);
}