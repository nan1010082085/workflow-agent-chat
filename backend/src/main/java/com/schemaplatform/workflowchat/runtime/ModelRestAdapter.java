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

  public ModelRestAdapter(RestClient client, RuntimeProperties props, ObjectMapper mapper) {
    this.client = client;
    this.props = props;
  }

  @Override
  public ModelCatalog listModels(String tenantId) {
    try {
      JsonNode body = client.get().uri(props.modelCatalogPath()).header("X-Tenant-Id", tenantId)
          .header("X-Chat-Internal", props.internalToken()).retrieve().body(JsonNode.class);
      JsonNode items = body == null ? null : body.get("items");
      List<ModelDto> result = new ArrayList<>();
      if (items != null && items.isArray()) {
        for (JsonNode item : items) {
          List<String> caps = new ArrayList<>();
          if (item.has("capabilities") && item.get("capabilities").isArray()) {
            item.get("capabilities").forEach(n -> caps.add(n.asText()));
          }
          result.add(new ModelDto(
              text(item, "id"), text(item, "name"), text(item, "model"), text(item, "provider"),
              caps, item.path("isDefault").asBoolean(false)));
        }
      }
      return new ModelCatalog(result, text(body, "defaultModelId"));
    } catch (Exception e) {
      throw new RuntimeUnavailableException("模型列表暂时不可用", e);
    }
  }

  @Override
  public CompletionResult complete(String tenantId, String modelId, List<Message> messages) {
    try {
      JsonNode body = client.post().uri(props.modelCompletionPath()).header("X-Tenant-Id", tenantId)
          .header("X-Chat-Internal", props.internalToken()).contentType(MediaType.APPLICATION_JSON)
          .body(Map.of("modelId", modelId, "messages", messages)).retrieve().body(JsonNode.class);

      String content = extractContent(body);
      String thinking = extractThinking(body);
      ThinkingExtractor.Split split = ThinkingExtractor.splitEmbedded(content);
      if ((thinking == null || thinking.isBlank()) && !split.thinking().isBlank()) {
        thinking = split.thinking();
        content = split.content();
      }
      return new CompletionResult(content, blankToNull(thinking));
    } catch (Exception e) {
      throw new RuntimeUnavailableException("模型暂时无法响应，请稍后重试", e);
    }
  }

  private static String extractContent(JsonNode body) {
    if (body == null) return "";
    String direct = firstNonBlank(text(body, "content"), text(body, "message"), text(body, "text"));
    if (!direct.isBlank()) return direct;
    JsonNode message = body.path("choices").path(0).path("message");
    return text(message, "content");
  }

  private static String extractThinking(JsonNode body) {
    if (body == null) return "";
    String direct = firstNonBlank(
        text(body, "thinking"),
        text(body, "reasoning"),
        text(body, "reasoning_content"),
        text(body, "reasoningContent"));
    if (!direct.isBlank()) return direct;
    JsonNode message = body.path("choices").path(0).path("message");
    return firstNonBlank(
        text(message, "thinking"),
        text(message, "reasoning_content"),
        text(message, "reasoningContent"));
  }

  private static String firstNonBlank(String... values) {
    for (String v : values) {
      if (v != null && !v.isBlank()) return v;
    }
    return "";
  }

  private static String text(JsonNode node, String field) {
    return node == null ? "" : node.path(field).asText("");
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
