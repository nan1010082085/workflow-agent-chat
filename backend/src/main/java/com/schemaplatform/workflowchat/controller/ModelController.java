package com.schemaplatform.workflowchat.controller;

import com.schemaplatform.workflowchat.runtime.ModelAdapter;
import com.schemaplatform.workflowchat.tenant.TenantContext;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat/models")
public class ModelController {
  private final ModelAdapter adapter;

  public ModelController(ModelAdapter adapter) {
    this.adapter = adapter;
  }

  @GetMapping
  public ModelAdapter.ModelCatalog list() {
    return adapter.listModels(TenantContext.tenantId());
  }

  @PostMapping("/completions")
  public CompletionResponse complete(@RequestBody CompletionRequest request) {
    ModelAdapter.CompletionResult result =
        adapter.complete(TenantContext.tenantId(), request.modelId(), request.messages());
    return new CompletionResponse(request.modelId(), result.content(), result.thinking());
  }

  public record CompletionRequest(String modelId, List<ModelAdapter.Message> messages) {}

  public record CompletionResponse(String modelId, String content, String thinking) {}
}
