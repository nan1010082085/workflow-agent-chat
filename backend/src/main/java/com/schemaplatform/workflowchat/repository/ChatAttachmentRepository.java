package com.schemaplatform.workflowchat.repository;

import com.schemaplatform.workflowchat.domain.ChatAttachment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatAttachmentRepository extends JpaRepository<ChatAttachment, String> {
  Optional<ChatAttachment> findByTenantIdAndId(String tenantId, String id);

  List<ChatAttachment> findByTenantIdAndMessageIdOrderByCreatedAtAsc(String tenantId, String messageId);

  List<ChatAttachment> findByTenantIdAndIdIn(String tenantId, List<String> ids);
}
