package com.schemaplatform.workflowchat.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;

/**
 * Runtime 执行状态的 Chat 侧稳定 DTO。
 * 由 RuntimeAdapter 从 Runtime 原始响应映射而来。见 ISS-02。
 */
public record ExecutionStatusDto(
    String executionId,
    RunStatusDto status,
    String output,
    String errorMessage,
    WaitingPayloadDto waiting,
    List<NodeTimelineDto> nodes,
    Instant startedAt,
    Instant finishedAt
) {
  public enum RunStatusDto {
    RUNNING, COMPLETED, FAILED, WAITING_INPUT, CANCELLED, UNKNOWN
  }

  public record WaitingPayloadDto(
      String prompt,
      List<FieldDto> fields,
      List<ActionDto> actions,
      boolean dangerous
  ) {}

  public record FieldDto(String key, String label, String type, List<String> options) {}

  public record ActionDto(String action, String label, String style) {}

  public record NodeTimelineDto(
      String nodeId,
      String nodeName,
      String status,
      Instant startedAt,
      Instant finishedAt
  ) {}
}
