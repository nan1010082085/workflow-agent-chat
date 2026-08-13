package com.schemaplatform.workflowchat.repository;

import com.schemaplatform.workflowchat.domain.ChatMessage;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, String> {

  List<ChatMessage> findByTenantIdAndSessionIdOrderByCreatedAtAsc(String tenantId, String sessionId);

  Optional<ChatMessage> findByTenantIdAndRuntimeExecutionIdAndRole(
      String tenantId, String runtimeExecutionId, ChatMessage.MessageRole role);
}
