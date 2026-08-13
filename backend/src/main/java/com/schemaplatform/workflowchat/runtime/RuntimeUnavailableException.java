package com.schemaplatform.workflowchat.runtime;

/**
 * Runtime 不可用异常。用于区分「业务失败」与「基础设施不可用」。
 * PRD 验收标准 6：Runtime 不可用时界面明确显示失败，不产生幽灵消息。
 */
public class RuntimeUnavailableException extends RuntimeException {

  public RuntimeUnavailableException(String message) {
    super(message);
  }

  public RuntimeUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
