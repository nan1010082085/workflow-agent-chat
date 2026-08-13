package com.schemaplatform.workflowchat.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schemaplatform.workflowchat.config.RuntimeProperties;
import com.schemaplatform.workflowchat.tenant.TenantContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Runtime REST 适配器。调用 Schema Platform AI Runtime Open API。
 *
 * <p><b>契约状态：未冻结</b>。请求/响应字段映射基于 ARCHITECTURE §4/§5 的过渡契约，
 * 待 Runtime 契约冻结后对齐字段名。见 docs/RUNTIME_ISSUES.md ISS-01/ISS-02。
 *
 * <p>所有调用带 X-Tenant-Id 与服务凭证头（见 ISS-03）。
 */
public class RuntimeRestAdapter implements RuntimeAdapter {

  private static final Logger log = LoggerFactory.getLogger(RuntimeRestAdapter.class);

  private final RestClient restClient;
  private final RuntimeProperties props;
  private final ObjectMapper objectMapper;

  public RuntimeRestAdapter(RestClient restClient, RuntimeProperties props, ObjectMapper objectMapper) {
    this.restClient = restClient;
    this.props = props;
    this.objectMapper = objectMapper;
  }

  @Override
  public List<AgentDto> listAgents(String tenantId) {
    // 过渡方案：调用平台管理 API。稳定契约待定（ISS-01）。
    try {
      JsonNode body = restClient.get()
          .uri(props.catalogPath())
          .header("X-Tenant-Id", tenantId)
          .header(props.catalogCredentialHeader(), props.catalogCredential())
          .retrieve()
          .body(JsonNode.class);
      return parseAgentCatalog(body);
    } catch (HttpClientErrorException.NotFound e) {
      log.warn("Agent catalog 接口 404，Runtime 可能未提供该聚合接口: {}", e.getMessage());
      return List.of();
    } catch (Exception e) {
      log.error("获取 Agent catalog 失败", e);
      throw new RuntimeUnavailableException("Agent catalog 不可用", e);
    }
  }

  @Override
  public InvokeResult invoke(InvokeRequest request) {
    Map<String, Object> requestBody = Map.of(
        "input", request.input(),
        "sessionId", request.sessionId(),
        "trigger", "chat"
    );
    String path = props.invokePathTemplate().replace("{slug}", request.slug());
    String correlationId = request.idempotencyKey();
    try {
      JsonNode body = restClient.post()
          .uri(path)
          .header("X-Tenant-Id", request.tenantId())
          .header(props.credentialHeader(), props.executionCredential())
          .header("Idempotency-Key", request.idempotencyKey())
          .header("X-Correlation-Id", correlationId)
          .contentType(MediaType.APPLICATION_JSON)
          .body(requestBody)
          .retrieve()
          .body(JsonNode.class);
      return parseInvokeResult(body);
    } catch (HttpClientErrorException e) {
      throw classifyError("invoke", request.slug(), correlationId, e);
    } catch (Exception e) {
      log.error("Runtime invoke 异常 slug={} corr={}", request.slug(), correlationId, e);
      throw new RuntimeUnavailableException("Runtime 不可用", e);
    }
  }

  @Override
  public ExecutionStatusDto getExecutionStatus(String runtimeExecutionId, String tenantId) {
    String path = props.executionPathTemplate().replace("{id}", runtimeExecutionId);
    String correlationId = UUID.randomUUID().toString();
    try {
      JsonNode body = restClient.get()
          .uri(path)
          .header("X-Tenant-Id", tenantId)
          .header(props.credentialHeader(), props.executionCredential())
          .header("X-Correlation-Id", correlationId)
          .retrieve()
          .body(JsonNode.class);
      return parseExecutionStatus(body);
    } catch (HttpClientErrorException.NotFound e) {
      throw new RuntimeUnavailableException("执行不存在: " + runtimeExecutionId, e);
    } catch (HttpClientErrorException e) {
      throw classifyError("status", runtimeExecutionId, correlationId, e);
    } catch (Exception e) {
      log.error("查询执行状态失败 executionId={} corr={}", runtimeExecutionId, correlationId, e);
      throw new RuntimeUnavailableException("执行状态查询失败", e);
    }
  }

  @Override
  public ExecutionStatusDto resume(String runtimeExecutionId, String tenantId, ResumeRequest request) {
    String path = props.resumePathTemplate().replace("{id}", runtimeExecutionId);
    try {
      JsonNode body = restClient.post()
          .uri(path)
          .header("X-Tenant-Id", tenantId)
          .header(props.credentialHeader(), props.executionCredential())
          .contentType(MediaType.APPLICATION_JSON)
          .body(Map.of("action", request.action(), "payload", request.payload()))
          .retrieve()
          .body(JsonNode.class);
      return parseExecutionStatus(body);
    } catch (Exception e) {
      log.error("resume 失败 executionId={}", runtimeExecutionId, e);
      throw new RuntimeUnavailableException("恢复执行失败", e);
    }
  }

  @Override
  public ExecutionStatusDto cancel(String runtimeExecutionId, String tenantId) {
    String path = props.cancelPathTemplate().replace("{id}", runtimeExecutionId);
    try {
      JsonNode body = restClient.post()
          .uri(path)
          .header("X-Tenant-Id", tenantId)
          .header(props.credentialHeader(), props.executionCredential())
          .retrieve()
          .body(JsonNode.class);
      return parseExecutionStatus(body);
    } catch (Exception e) {
      log.error("cancel 失败 executionId={}", runtimeExecutionId, e);
      throw new RuntimeUnavailableException("取消执行失败", e);
    }
  }

  // ---- 字段映射（契约未冻结，宽松解析）----

  /**
   * 错误归因（B-02）。按 HTTP 状态区分 Runtime 故障类型，带 correlation id 便于追踪。
   */
  private RuntimeUnavailableException classifyError(String op, String target, String correlationId,
      HttpClientErrorException e) {
    int code = e.getStatusCode().value();
    String msg = "Runtime " + op + " 失败 [" + code + "] target=" + target + " corr=" + correlationId;
    log.warn(msg);
    return switch (code) {
      case 404 -> new RuntimeUnavailableException("请求的资源不存在: " + target, e);
      case 409 -> new RuntimeUnavailableException("状态冲突，操作无法执行: " + target, e);
      case 429 -> new RuntimeUnavailableException("请求过于频繁，请稍后重试", e);
      default -> code >= 500
          ? new RuntimeUnavailableException("Runtime 暂时不可用，请稍后重试", e)
          : new RuntimeUnavailableException("Runtime 调用失败 [" + code + "]", e);
    };
  }

  private List<AgentDto> parseAgentCatalog(JsonNode body) {
    if (body == null) return List.of();
    JsonNode items = body.has("data") ? body.get("data") : body;
    if (items != null && items.has("items")) items = items.get("items");
    if (!items.isArray()) return List.of();
    List<AgentDto> agents = new ArrayList<>();
    for (JsonNode item : items) {
      // 只取已发布的；草稿过滤（ISS-01 待确认字段名）
      boolean published = item.has("published") ? item.get("published").asBoolean()
          : "published".equalsIgnoreCase(text(item, "status"));
      if (!published) continue;
      agents.add(new AgentDto(
          text(item, "id"),
          text(item, "slug"),
          text(item, "name"),
          text(item, "description"),
          text(item, "icon"),
          stringList(item, "supportedInputs"),
          item.has("hitlCapable") && item.get("hitlCapable").asBoolean(),
          text(item, "version"),
          text(item, "updatedAt"),
          true
      ));
    }
    return agents;
  }

  private InvokeResult parseInvokeResult(JsonNode body) {
    if (body == null) {
      throw new RuntimeUnavailableException("Runtime invoke 响应为空");
    }
    JsonNode data = body.has("data") ? body.get("data") : body;
    String executionId = text(data, "executionId");
    if (executionId.isBlank()) executionId = text(data, "id");
    String statusText = text(data, "status").toLowerCase();
    ExecutionStatusDto.RunStatusDto status = mapStatus(statusText);
    String error = text(data, "error");
    return new InvokeResult(executionId, status, error);
  }

  private ExecutionStatusDto parseExecutionStatus(JsonNode body) {
    if (body == null) return null;
    JsonNode data = body.has("data") ? body.get("data") : body;
    String executionId = text(data, "executionId");
    if (executionId.isBlank()) executionId = text(data, "id");
    ExecutionStatusDto.RunStatusDto status = mapStatus(text(data, "status").toLowerCase());
    String output = asText(data, "output");
    String errorMessage = text(data, "error");
    ExecutionStatusDto.WaitingPayloadDto waiting = parseWaiting(data.get("waiting"));
    List<ExecutionStatusDto.NodeTimelineDto> nodes = parseNodes(data.get("nodes"));
    Instant startedAt = parseInstant(data, "startedAt");
    Instant finishedAt = parseInstant(data, "finishedAt");
    return new ExecutionStatusDto(executionId, status, output, errorMessage, waiting, nodes, startedAt, finishedAt);
  }

  private ExecutionStatusDto.WaitingPayloadDto parseWaiting(JsonNode waiting) {
    if (waiting == null || waiting.isMissingNode() || waiting.isNull()) return null;
    String prompt = text(waiting, "prompt");
    List<ExecutionStatusDto.FieldDto> fields = new ArrayList<>();
    JsonNode fieldsNode = waiting.get("fields");
    if (fieldsNode != null && fieldsNode.isArray()) {
      for (JsonNode f : fieldsNode) {
        fields.add(new ExecutionStatusDto.FieldDto(
            text(f, "key"), text(f, "label"), text(f, "type"), stringList(f, "options")));
      }
    }
    List<ExecutionStatusDto.ActionDto> actions = new ArrayList<>();
    JsonNode actionsNode = waiting.get("actions");
    if (actionsNode != null && actionsNode.isArray()) {
      for (JsonNode a : actionsNode) {
        actions.add(new ExecutionStatusDto.ActionDto(
            text(a, "action"), text(a, "label"), text(a, "style")));
      }
    }
    boolean dangerous = waiting.has("dangerous") && waiting.get("dangerous").asBoolean();
    return new ExecutionStatusDto.WaitingPayloadDto(prompt, fields, actions, dangerous);
  }

  private List<ExecutionStatusDto.NodeTimelineDto> parseNodes(JsonNode nodes) {
    if (nodes == null || !nodes.isArray()) return List.of();
    List<ExecutionStatusDto.NodeTimelineDto> result = new ArrayList<>();
    for (JsonNode n : nodes) {
      result.add(new ExecutionStatusDto.NodeTimelineDto(
          text(n, "nodeId"), text(n, "nodeName"), text(n, "status"),
          parseInstant(n, "startedAt"), parseInstant(n, "finishedAt")));
    }
    return result;
  }

  private ExecutionStatusDto.RunStatusDto mapStatus(String runtimeStatus) {
    return switch (runtimeStatus) {
      case "running" -> ExecutionStatusDto.RunStatusDto.RUNNING;
      case "success", "completed" -> ExecutionStatusDto.RunStatusDto.COMPLETED;
      case "error", "failed" -> ExecutionStatusDto.RunStatusDto.FAILED;
      case "waiting" -> ExecutionStatusDto.RunStatusDto.WAITING_INPUT;
      case "cancelled" -> ExecutionStatusDto.RunStatusDto.CANCELLED;
      default -> ExecutionStatusDto.RunStatusDto.UNKNOWN;
    };
  }

  // ---- JSON 小工具 ----

  private String text(JsonNode node, String field) {
    if (node == null || !node.has(field) || node.get(field).isNull()) return "";
    return node.get(field).asText();
  }

  private String asText(JsonNode node, String field) {
    JsonNode v = node == null ? null : node.get(field);
    if (v == null || v.isNull()) return "";
    if (v.isTextual()) return v.asText();
    try {
      return objectMapper.writeValueAsString(v);
    } catch (Exception e) {
      return v.toString();
    }
  }

  private List<String> stringList(JsonNode node, String field) {
    if (node == null || !node.has(field)) return List.of();
    JsonNode arr = node.get(field);
    if (!arr.isArray()) return List.of();
    List<String> result = new ArrayList<>();
    arr.forEach(e -> result.add(e.asText()));
    return result;
  }

  private Instant parseInstant(JsonNode node, String field) {
    String v = text(node, field);
    if (v.isBlank()) return null;
    try {
      return Instant.parse(v);
    } catch (Exception e) {
      return null;
    }
  }
}
