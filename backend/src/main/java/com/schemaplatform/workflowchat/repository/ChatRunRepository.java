package com.schemaplatform.workflowchat.repository;

import com.schemaplatform.workflowchat.domain.ChatRun;
import com.schemaplatform.workflowchat.domain.RunStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRunRepository extends JpaRepository<ChatRun, String> {

  Optional<ChatRun> findByIdAndTenantId(String id, String tenantId);

  Optional<ChatRun> findByTenantIdAndRuntimeExecutionId(String tenantId, String runtimeExecutionId);

  /** 查询某会话下处于非终态的 run，用于幂等/串行校验（B-06）。 */
  List<ChatRun> findByTenantIdAndSessionIdAndStatusIn(
      String tenantId, String sessionId, List<RunStatus> statuses);
}
