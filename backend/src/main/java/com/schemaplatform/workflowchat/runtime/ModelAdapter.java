package com.schemaplatform.workflowchat.runtime;

import java.util.List;

public interface ModelAdapter {
  ModelCatalog listModels(String tenantId);

  /** 模型补全：返回正文与可选思考过程。 */
  CompletionResult complete(String tenantId, String modelId, List<Message> messages);

  record ModelCatalog(List<ModelDto> items, String defaultModelId) {}

  record Message(String role, String content) {}

  record CompletionResult(String content, String thinking) {
    public static CompletionResult ofContent(String content) {
      return new CompletionResult(content == null ? "" : content, null);
    }
  }
}
