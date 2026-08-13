package com.schemaplatform.workflowchat.common;

import java.time.Instant;
import java.util.List;

/**
 * 统一错误响应。所有异常经 GlobalExceptionHandler 转换为该结构。
 */
public record ErrorResponse(
    String error,
    String message,
    String code,
    String traceId,
    Instant timestamp,
    List<String> details
) {
  public static ErrorResponse of(String code, String message) {
    return new ErrorResponse("request_failed", message, code, null, Instant.now(), List.of());
  }

  public static ErrorResponse of(String code, String message, List<String> details) {
    return new ErrorResponse("request_failed", message, code, null, Instant.now(), details);
  }
}
