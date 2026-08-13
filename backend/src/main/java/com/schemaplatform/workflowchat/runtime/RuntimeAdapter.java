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

  /**
   * 澄语会话历史回合（映射平台 {@code input.history}）。
   * role 仅用 user / assistant / system。
   */
  record HistoryTurn(String role, String content) {}

  /**
   * 传给平台 document-parse 的文件流（input.file / input.files[0]）。
   * contentBase64 为原始文件字节的 Base64。
   */
  record InvokeFile(String filename, String mimetype, String contentBase64) {}

  record InvokeRequest(
      String slug,
      String input,
      String tenantId,
      String userId,
      String sessionId,
      String idempotencyKey,
      /** 本轮之前的对话；不含当前 user message */
      List<HistoryTurn> history,
      /** 可选附件，映射为平台 $input.file */
      List<InvokeFile> files
  ) {
    public InvokeRequest {
      history = history == null ? List.of() : List.copyOf(history);
      files = files == null ? List.of() : List.copyOf(files);
    }

    /** 无附件时的便捷构造。 */
    public InvokeRequest(
        String slug, String input, String tenantId, String userId,
        String sessionId, String idempotencyKey, List<HistoryTurn> history) {
      this(slug, input, tenantId, userId, sessionId, idempotencyKey, history, List.of());
    }

    /** 无历史时的便捷构造（mock / 单测）。 */
    public InvokeRequest(
        String slug, String input, String tenantId, String userId,
        String sessionId, String idempotencyKey) {
      this(slug, input, tenantId, userId, sessionId, idempotencyKey, List.of(), List.of());
    }
  }

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
