package com.schemaplatform.workflowchat.common;

/**
 * 触发 429 限流响应。
 */
public class RateLimitExceededException extends RuntimeException {
  public RateLimitExceededException(String message) {
    super(message);
  }
}
