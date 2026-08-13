package com.schemaplatform.workflowchat.runtime;

import java.util.List;

/**
 * Runtime 适配器接口。Chat 只依赖该接口，不直接耦合 Runtime 实现。
 *
 * <p>实现有两套：
 * <ul>
 *   <li>{@link RuntimeRestAdapter} —— 真实 Runtime REST 调用</li>
 *   <li>{@link RuntimeMockAdapter} —— mock fallback，供前端联调（见 ISS-07）</li>
 * </ul>
 * 契约字段映射在实现内部完成，见 docs/RUNTIME_ISSUES.md ISS-01/ISS-02。
 */
public interface RuntimeAdapter {

  /** 获取当前租户可见的已发布 Agent 列表。 */
  List<AgentDto> listAgents(String tenantId);

  /** 调用 Workflow，返回 runtimeExecutionId。 */
  InvokeResult invoke(InvokeRequest request);

  /** 查询执行状态。 */
  ExecutionStatusDto getExecutionStatus(String runtimeExecutionId, String tenantId);

  /** 恢复 HITL 等待。 */
  ExecutionStatusDto resume(String runtimeExecutionId, String tenantId, ResumeRequest request);

  /** 取消执行。 */
  ExecutionStatusDto cancel(String runtimeExecutionId, String tenantId);

  record InvokeRequest(
      String slug,
      String input,
      String tenantId,
      String userId,
      String sessionId,
      String idempotencyKey
  ) {}

  record InvokeResult(
      String runtimeExecutionId,
      ExecutionStatusDto.RunStatusDto initialStatus,
      String error
  ) {}

  record ResumeRequest(
      String action,
      String payload
  ) {}
}
