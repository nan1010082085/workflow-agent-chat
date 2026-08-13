package com.schemaplatform.workflowchat.controller;

import com.schemaplatform.workflowchat.service.RunSyncService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Run 接口。对应 ARCHITECTURE §3 的 run 相关 API。
 * GET  /api/chat/runs/{id}       —— 查询/同步状态
 * POST /api/chat/runs/{id}/resume —— HITL 恢复
 * POST /api/chat/runs/{id}/cancel —— 取消运行
 */
@RestController
@RequestMapping("/api/chat/runs")
public class RunController {

  private final RunSyncService runSyncService;

  public RunController(RunSyncService runSyncService) {
    this.runSyncService = runSyncService;
  }

  @GetMapping("/by-execution/{executionId}")
  public RunSyncService.RunStatusView getByExecution(@PathVariable String executionId) {
    return runSyncService.syncByExecutionId(executionId);
  }

  @GetMapping("/{runId}")
  public RunSyncService.RunStatusView getRun(@PathVariable String runId) {
    return runSyncService.syncRun(runId);
  }

  @PostMapping("/{runId}/resume")
  public RunSyncService.RunStatusView resume(
      @PathVariable String runId,
      @RequestBody ResumeRequest request) {
    return runSyncService.resume(runId, request.action(), request.payload());
  }

  @PostMapping("/{runId}/cancel")
  public RunSyncService.RunStatusView cancel(@PathVariable String runId) {
    return runSyncService.cancel(runId);
  }

  public record ResumeRequest(@NotBlank String action, String payload) {}
}
