package com.schemaplatform.workflowchat.auth;

/**
 * 未登录或令牌无效。
 */
public class UnauthorizedException extends RuntimeException {
  public UnauthorizedException(String message) {
    super(message);
  }
}
