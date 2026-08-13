package com.schemaplatform.workflowchat.service;

import com.schemaplatform.workflowchat.domain.ChatRun;
import com.schemaplatform.workflowchat.domain.RunStatus;
import com.schemaplatform.workflowchat.repository.ChatRunRepository;
import com.schemaplatform.workflowchat.tenant.TenantContext;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Run 服务。管理 Runtime 执行映射与状态流转。
 */
@Service
public class RunService {

  private final ChatRunRepository runRepository;

  public RunService(ChatRunRepository runRepository) {
    this.runRepository = runRepository;
  }

  @Transactional
  public ChatRun startRun(String sessionId, String agentId, String runtimeExecutionId) {
    String tenantId = TenantContext.tenantId();
    ChatRun run = ChatRun.start(
        UUID.randomUUID().toString(), tenantId, sessionId, agentId, runtimeExecutionId);
    return runRepository.save(run);
  }

  @Transactional(readOnly = true)
  public ChatRun getRun(String runId) {
    String tenantId = TenantContext.tenantId();
    return runRepository.findByIdAndTenantId(runId, tenantId)
        .orElseThrow(() -> new NoSuchElementException("运行不存在或无权访问: " + runId));
  }

  @Transactional
  public ChatRun updateStatus(String runId, ChatRun updated) {
    ChatRun run = getRun(runId);
    // 状态流转由 ChatRun 自身方法保证
    return runRepository.save(updated);
  }

  @Transactional
  public ChatRun save(ChatRun run) {
    return runRepository.save(run);
  }

  @Transactional(readOnly = true)
  public ChatRun findByExecutionId(String runtimeExecutionId) {
    String tenantId = TenantContext.tenantId();
    return runRepository.findByTenantIdAndRuntimeExecutionId(tenantId, runtimeExecutionId).orElse(null);
  }

  /**
   * 幂等/串行校验（B-06）：一个会话同时只允许一个进行中的任务。
   * 重复发送在入口拒绝，避免重复执行和幽灵消息。
   */
  @Transactional(readOnly = true)
  public boolean hasActiveRun(String sessionId) {
    String tenantId = TenantContext.tenantId();
    return !runRepository
        .findByTenantIdAndSessionIdAndStatusIn(tenantId, sessionId,
            List.of(RunStatus.RUNNING, RunStatus.WAITING_INPUT))
        .isEmpty();
  }
}
