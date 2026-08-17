package com.schemaplatform.workflowchat.runtime;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runtime mock 适配器。当 workflow-key 为空或 mockEnabled=true 时启用。
 *
 * <p>用途：前端联调阶段，不依赖真实 Runtime（见 ISS-07）。
 * 模拟一个异步执行：invoke 后先 running，查询时返回 completed + 假结果；
 * 若 slug 以 "hitl-" 开头则返回 waiting，resume 后返回 completed。
 */
public class RuntimeMockAdapter implements RuntimeAdapter {

  private static final Logger log = LoggerFactory.getLogger(RuntimeMockAdapter.class);

  private static final List<AgentDto> MOCK_AGENTS = List.of(
      new AgentDto("expense-audit", "expense-audit", "报销审核",
          "审核报销材料并识别风险项，输出结构化结论", "📋",
          List.of("text", "file", "image"), true, "1.0.0", "2026-08-10", true),
      new AgentDto("document-summary", "document-summary", "文档摘要",
          "提取长文档中的重点结论与待办", "📄",
          List.of("text", "file", "document"), false, "1.0.0", "2026-08-10", true),
      new AgentDto("contract-check", "contract-check", "合同检查",
          "校验合同条款合规性与风险点", "📑",
          List.of("text", "file"), true, "1.0.0", "2026-08-11", true)
  );

  // 内存态执行记录，模拟 Runtime
  private final Map<String, MockExecution> executions = new ConcurrentHashMap<>();

  @Override
  public List<AgentDto> listAgents(String tenantId) {
    log.debug("[MOCK] listAgents tenant={}", tenantId);
    return MOCK_AGENTS;
  }

  @Override
  public InvokeResult invoke(InvokeRequest request) {
    String executionId = UUID.randomUUID().toString();
    boolean isHitl = request.slug().startsWith("hitl-") || "expense-audit".equals(request.slug());
    ExecutionStatusDto.RunStatusDto initial = isHitl
        ? ExecutionStatusDto.RunStatusDto.WAITING_INPUT
        : ExecutionStatusDto.RunStatusDto.RUNNING;
    MockExecution exec = new MockExecution(executionId, request.slug(), initial);
    exec.input = request.input();
    executions.put(executionId, exec);
    log.info("[MOCK] invoke slug={} -> executionId={} initial={}", request.slug(), executionId, initial);
    return new InvokeResult(executionId, initial, null);
  }

  @Override
  public ExecutionStatusDto getExecutionStatus(String runtimeExecutionId, String tenantId) {
    MockExecution exec = executions.get(runtimeExecutionId);
    if (exec == null) {
      return new ExecutionStatusDto(runtimeExecutionId,
          ExecutionStatusDto.RunStatusDto.UNKNOWN, null, null, "执行不存在: " + runtimeExecutionId,
          null, List.of(), Instant.now(), null);
    }
    // 非 HITL 的 running 推进到 completed
    if (exec.status == ExecutionStatusDto.RunStatusDto.RUNNING) {
      exec.status = ExecutionStatusDto.RunStatusDto.COMPLETED;
      exec.thinking = "1. 解析用户输入\n2. 匹配规则库\n3. 汇总结论（mock）";
      exec.output = "已根据输入「" + truncate(exec.input, 30) + "」完成任务。\n\n**执行摘要**\n- 解析输入材料\n- 匹配规则库\n- 生成结论\n\n结论：该任务正常完成。";
    }
    ExecutionStatusDto.WaitingPayloadDto waiting = null;
    if (exec.status == ExecutionStatusDto.RunStatusDto.WAITING_INPUT && exec.waitingPayload == null) {
      exec.waitingPayload = new ExecutionStatusDto.WaitingPayloadDto(
          "该任务需要你确认是否继续执行报销审批。即将生成最终审核结论。",
          List.of(),
          List.of(
              new ExecutionStatusDto.ActionDto("approve", "批准并继续", "primary"),
              new ExecutionStatusDto.ActionDto("reject", "拒绝", "danger")
          ),
          false
      );
    }
    if (exec.status == ExecutionStatusDto.RunStatusDto.WAITING_INPUT) {
      waiting = exec.waitingPayload;
    }
    return new ExecutionStatusDto(runtimeExecutionId, exec.status,
        exec.output, exec.thinking, exec.error, waiting, List.of(), exec.startedAt, exec.finishedAt,
        null, null, null, buildMockWorkflowExecution(exec));
  }

  @Override
  public ExecutionStatusDto resume(String runtimeExecutionId, String tenantId, ResumeRequest request) {
    MockExecution exec = executions.get(runtimeExecutionId);
    if (exec == null) {
      throw new RuntimeUnavailableException("执行不存在: " + runtimeExecutionId);
    }
    if ("reject".equals(request.action())) {
      exec.status = ExecutionStatusDto.RunStatusDto.CANCELLED;
      exec.finishedAt = Instant.now();
      exec.error = "用户拒绝了审批";
    } else {
      exec.status = ExecutionStatusDto.RunStatusDto.COMPLETED;
      exec.finishedAt = Instant.now();
      exec.thinking = "用户已确认「" + request.action() + "」，继续生成最终结论（mock）。";
      exec.output = "已根据用户「" + request.action() + "」继续执行，任务已完成。\n\n**最终结论**\n审核通过，报销单合规。";
    }
    return getExecutionStatus(runtimeExecutionId, tenantId);
  }

  @Override
  public ExecutionStatusDto cancel(String runtimeExecutionId, String tenantId) {
    MockExecution exec = executions.get(runtimeExecutionId);
    if (exec != null) {
      exec.status = ExecutionStatusDto.RunStatusDto.CANCELLED;
      exec.finishedAt = Instant.now();
      exec.error = "用户取消了运行";
    }
    return getExecutionStatus(runtimeExecutionId, tenantId);
  }

  private String truncate(String s, int max) {
    if (s == null) return "";
    return s.length() > max ? s.substring(0, max) + "…" : s;
  }

  private static class MockExecution {
    final String executionId;
    final String slug;
    ExecutionStatusDto.RunStatusDto status;
    String input;
    String output;
    String thinking;
    String error;
    ExecutionStatusDto.WaitingPayloadDto waitingPayload;
    final Instant startedAt;
    Instant finishedAt;

    MockExecution(String executionId, String slug, ExecutionStatusDto.RunStatusDto initial) {
      this.executionId = executionId;
      this.slug = slug;
      this.status = initial;
      this.startedAt = Instant.now();
    }
  }


  private String buildMockWorkflowExecution(MockExecution exec) {
    Map<String, Object> execution = new java.util.LinkedHashMap<>();
    execution.put("executionId", exec.executionId);
    execution.put("workflowId", exec.slug);
    execution.put("workflowName", exec.slug);
    execution.put("status", exec.status.name());
    execution.put("startedAt", exec.startedAt.toString());
    if (exec.finishedAt != null) {
      execution.put("finishedAt", exec.finishedAt.toString());
      execution.put("durationMs", java.time.Duration.between(exec.startedAt, exec.finishedAt).toMillis());
    }
    try {
      return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(execution);
    } catch (Exception e) {
      return null;
    }
  }
}