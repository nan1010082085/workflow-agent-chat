package com.schemaplatform.workflowchat.runtime;

import java.util.List;

public interface ModelAdapter {
  ModelCatalog listModels(String tenantId);
  String complete(String tenantId, String modelId, List<Message> messages);
  record ModelCatalog(List<ModelDto> items, String defaultModelId) {}
  record Message(String role, String content) {}
}
