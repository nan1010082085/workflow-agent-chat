package com.schemaplatform.workflowchat.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schemaplatform.workflowchat.config.RuntimeProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

public class ModelRestAdapter implements ModelAdapter {
  private final RestClient client;
  private final RuntimeProperties props;
  private final ObjectMapper mapper;

  public ModelRestAdapter(RestClient client, RuntimeProperties props, ObjectMapper mapper) {
    this.client = client; this.props = props; this.mapper = mapper;
  }

  @Override public ModelCatalog listModels(String tenantId) {
    try {
      JsonNode body = client.get().uri(props.modelCatalogPath()).header("X-Tenant-Id", tenantId)
          .header("X-Chat-Internal", props.internalToken()).retrieve().body(JsonNode.class);
      JsonNode items = body == null ? null : body.get("items");
      List<ModelDto> result = new ArrayList<>();
      if (items != null && items.isArray()) for (JsonNode item : items) {
        List<String> caps = new ArrayList<>();
        if (item.has("capabilities") && item.get("capabilities").isArray()) item.get("capabilities").forEach(n -> caps.add(n.asText()));
        result.add(new ModelDto(text(item, "id"), text(item, "name"), text(item, "model"), text(item, "provider"), caps, item.path("isDefault").asBoolean(false)));
      }
      return new ModelCatalog(result, text(body, "defaultModelId"));
    } catch (Exception e) { throw new RuntimeUnavailableException("模型列表暂时不可用", e); }
  }

  @Override public String complete(String tenantId, String modelId, List<Message> messages) {
    try {
      JsonNode body = client.post().uri(props.modelCompletionPath()).header("X-Tenant-Id", tenantId)
          .header("X-Chat-Internal", props.internalToken()).contentType(MediaType.APPLICATION_JSON)
          .body(Map.of("modelId", modelId, "messages", messages)).retrieve().body(JsonNode.class);
      return text(body, "content");
    } catch (Exception e) { throw new RuntimeUnavailableException("模型暂时无法响应，请稍后重试", e); }
  }

  private static String text(JsonNode node, String field) { return node == null ? "" : node.path(field).asText(""); }
}
