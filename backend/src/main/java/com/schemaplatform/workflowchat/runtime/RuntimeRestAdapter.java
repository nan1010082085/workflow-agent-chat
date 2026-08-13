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
          .header("X-Chat-Internal", props.internalToken())
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
    // 平台节点读 input.message；多轮上下文经 input.history → conversationHistory
    Map<String, Object> input = new java.util.LinkedHashMap<>();
    input.put("message", request.input());
    if (request.userId() != null && !request.userId().isBlank()) {
      input.put("userId", request.userId());
    }
    if (request.history() != null && !request.history().isEmpty()) {
      List<Map<String, String>> turns = new ArrayList<>();
      StringBuilder historyText = new StringBuilder();
      for (RuntimeAdapter.HistoryTurn turn : request.history()) {
        if (turn == null || turn.content() == null || turn.content().isBlank()) continue;
        String role = turn.role() == null ? "user" : turn.role().trim().toLowerCase();
        if (!role.equals("user") && !role.equals("assistant") && !role.equals("system")) {
          role = "user";
        }
        turns.add(Map.of("role", role, "content", turn.content().trim()));
        String label = switch (role) {
          case "assistant" -> "助手";
          case "system" -> "系统";
          default -> "用户";
        };
        historyText.append(label).append("：").append(turn.content().trim()).append('\n');
      }
      if (!turns.isEmpty()) {
        input.put("history", turns);
        input.put("conversationHistory", turns);
        input.put("historyText", historyText.toString().trim());
      }
    }
    Map<String, Object> requestBody = new java.util.LinkedHashMap<>();
    requestBody.put("input", input);
    requestBody.put("sessionId", request.sessionId());
    requestBody.put("trigger", "chat");

    String path = props.invokePathTemplate().replace("{slug}", request.slug());
    String correlationId = request.idempotencyKey();
    try {
      JsonNode body = applyCredentials(
          restClient.post()
              .uri(path)
              .header("X-Tenant-Id", request.tenantId())
              .header("Idempotency-Key", request.idempotencyKey())
              .header("X-Correlation-Id", correlationId)
              .contentType(MediaType.APPLICATION_JSON)
              .body(requestBody))
          .retrieve()
          .body(JsonNode.class);
      log.info("Runtime invoke slug={} session={} historyTurns={} corr={}",
          request.slug(), request.sessionId(),
          request.history() == null ? 0 : request.history().size(), correlationId);
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
      JsonNode body = applyCredentials(
          restClient.get()
              .uri(path)
              .header("X-Tenant-Id", tenantId)
              .header("X-Correlation-Id", correlationId))
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
    // 平台契约：body 为 approved/comment；Chat 侧 action=approve|reject
    String action = request.action() == null ? "approve" : request.action().trim().toLowerCase();
    boolean approved = !(action.equals("reject") || action.equals("deny") || action.equals("cancel"));
    Map<String, Object> body = new java.util.LinkedHashMap<>();
    body.put("approved", approved);
    body.put("action", action);
    body.put("answers", Map.of());
    if (request.payload() != null && !request.payload().isBlank()) {
      body.put("comment", request.payload());
      body.put("payload", request.payload());
      Map<String, String> answers = tryParseAnswers(request.payload());
      if (answers != null) {
        body.put("answers", answers);
      }
    }
    try {
      JsonNode resp = applyCredentials(
          restClient.post()
              .uri(path)
              .header("X-Tenant-Id", tenantId)
              .contentType(MediaType.APPLICATION_JSON)
              .body(body))
          .retrieve()
          .body(JsonNode.class);
      ExecutionStatusDto parsed = parseExecutionStatus(resp);
      // resume 成功后平台多为 async running；若仍解析为 waiting 则按 running 推进 Chat 状态
      if (parsed != null && parsed.status() == ExecutionStatusDto.RunStatusDto.WAITING_INPUT) {
        return new ExecutionStatusDto(
            parsed.executionId(),
            ExecutionStatusDto.RunStatusDto.RUNNING,
            parsed.output(),
            parsed.thinking(),
            parsed.errorMessage(),
            null,
            parsed.nodes(),
            parsed.startedAt(),
            parsed.finishedAt());
      }
      return parsed;
    } catch (Exception e) {
      log.error("resume 失败 executionId={}", runtimeExecutionId, e);
      throw new RuntimeUnavailableException("恢复执行失败", e);
    }
  }

  @Override
  public ExecutionStatusDto cancel(String runtimeExecutionId, String tenantId) {
    String path = props.cancelPathTemplate().replace("{id}", runtimeExecutionId);
    try {
      JsonNode body = applyCredentials(
          restClient.post()
              .uri(path)
              .header("X-Tenant-Id", tenantId))
          .retrieve()
          .body(JsonNode.class);
      return parseExecutionStatus(body);
    } catch (Exception e) {
      log.error("cancel 失败 executionId={}", runtimeExecutionId, e);
      throw new RuntimeUnavailableException("取消执行失败", e);
    }
  }

  /**
   * 执行面凭证：优先 X-Chat-Internal（与 catalog 同源）；
   * 若配置了 workflow/API key 则额外附带，便于过渡期兼容。
   */
  private RestClient.RequestHeadersSpec<?> applyCredentials(RestClient.RequestHeadersSpec<?> spec) {
    String internal = props.internalToken();
    if (internal != null && !internal.isBlank()) {
      spec = spec.header("X-Chat-Internal", internal);
    }
    String credential = props.executionCredential();
    if (credential != null && !credential.isBlank()) {
      spec = spec.header(props.credentialHeader(), credential);
    }
    return spec;
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
    JsonNode nodeRecords = data.has("nodeRecords") ? data.get("nodeRecords") : data.get("nodes");
    ExecutionStatusDto.WaitingPayloadDto waiting = resolveWaiting(status, data.get("waiting"), nodeRecords);
    waiting = enrichWaitingFromAnalyzer(waiting, nodeRecords, status);

    String rawTop = extractTextFromOutput(data.get("output"));
    if (status == ExecutionStatusDto.RunStatusDto.WAITING_INPUT
        && isNonProseNodeDump(data.get("output"), rawTop)) {
      rawTop = null;
    }
    String output;
    if (status == ExecutionStatusDto.RunStatusDto.WAITING_INPUT) {
      output = firstNonBlank(
          rawTop,
          streamingText(data.get("streamingOutput")),
          buildWaitingVisibleContent(nodeRecords, waiting));
    } else {
      output = firstNonBlank(
          rawTop,
          extractNodeText(nodeRecords),
          streamingText(data.get("streamingOutput")));
    }
    String thinking = firstNonBlank(
        asText(data, "thinking"),
        asText(data, "reasoning"),
        asText(data, "reasoningContent"));
    ThinkingExtractor.Split split = ThinkingExtractor.splitEmbedded(output == null ? "" : output);
    if ((thinking == null || thinking.isBlank()) && !split.thinking().isBlank()) {
      thinking = split.thinking();
      output = split.content();
    }
    String errorMessage = text(data, "error");
    List<ExecutionStatusDto.NodeTimelineDto> nodes = parseNodes(nodeRecords);
    Instant startedAt = parseInstant(data, "startedAt");
    Instant finishedAt = parseInstant(data, "finishedAt");
    return new ExecutionStatusDto(
        executionId, status, blankToNull(output), blankToNull(thinking), errorMessage, waiting, nodes, startedAt, finishedAt);
  }

  /**
   * 节点结构化 dump（需求分析/路由等）不应直接当用户可见正文。
   */
  private boolean isNonProseNodeDump(JsonNode output, String extracted) {
    if (output != null && output.isObject()) {
      if (output.has("confirmQuestions") || output.has("completeness")
          || output.has("recommendedExperts") || output.has("routeReason")
          || output.has("expertId") || output.has("chainPreview")) {
        return true;
      }
      if (output.has("intent") && !hasNonBlankTextField(output)) {
        return true;
      }
      // HITL 等待壳：仅 message + confirmQuestions，正文由 buildWaitingVisibleContent 组装
      if (output.has("confirmQuestions") && output.has("message") && !hasNonBlankTextField(output)) {
        return true;
      }
    }
    if (extracted == null || extracted.isBlank()) return false;
    String t = extracted.trim();
    return (t.startsWith("{") && t.endsWith("}")) || (t.startsWith("[") && t.endsWith("]"));
  }

  private boolean hasNonBlankTextField(JsonNode output) {
    for (String key : List.of("text", "content", "answer")) {
      String v = text(output, key);
      if (v != null && !v.isBlank()) return true;
    }
    return false;
  }

  /**
   * 将需求分析节点的提问合并进 waiting.fields，供确认卡展示。
   */
  private ExecutionStatusDto.WaitingPayloadDto enrichWaitingFromAnalyzer(
      ExecutionStatusDto.WaitingPayloadDto waiting,
      JsonNode nodeRecords,
      ExecutionStatusDto.RunStatusDto status) {
    if (status != ExecutionStatusDto.RunStatusDto.WAITING_INPUT || waiting == null) {
      return waiting;
    }
    List<ExecutionStatusDto.FieldDto> extra = extractAnalyzerQuestions(nodeRecords);
    if (extra.isEmpty()) return waiting;
    List<ExecutionStatusDto.FieldDto> merged = new ArrayList<>();
    if (waiting.fields() != null) merged.addAll(waiting.fields());
    java.util.HashSet<String> keys = new java.util.HashSet<>();
    for (ExecutionStatusDto.FieldDto f : merged) {
      if (f.key() != null) keys.add(f.key());
    }
    for (ExecutionStatusDto.FieldDto f : extra) {
      if (f.key() != null && keys.add(f.key())) {
        merged.add(f);
      }
    }
    return new ExecutionStatusDto.WaitingPayloadDto(
        waiting.prompt(), merged, waiting.actions(), waiting.dangerous());
  }

  /**
   * 等待确认时的用户可见正文：分析结果摘要 + 待澄清问题。
   */
  private String buildWaitingVisibleContent(
      JsonNode nodeRecords, ExecutionStatusDto.WaitingPayloadDto waiting) {
    StringBuilder sb = new StringBuilder();
    JsonNode analyzer = findNodeOutput(nodeRecords, "requirement-analyzer");
    if (analyzer != null && analyzer.isObject()) {
      String intent = text(analyzer, "intent");
      String completeness = analyzer.has("completeness") && !analyzer.get("completeness").isNull()
          ? analyzer.get("completeness").asText()
          : "";
      sb.append("## 需求理解\n\n");
      if (!intent.isBlank()) {
        sb.append("- 意图：").append(intent).append('\n');
      }
      if (!completeness.isBlank()) {
        sb.append("- 完整度：").append(completeness).append("%\n");
      }
      List<String> qs = readQuestionTexts(analyzer.get("confirmQuestions"));
      if (!qs.isEmpty()) {
        sb.append("\n## 需要你补充\n\n");
        for (int i = 0; i < qs.size(); i++) {
          sb.append(i + 1).append(". ").append(qs.get(i)).append('\n');
        }
      }
    }
    String interrupt = "";
    JsonNode waitingOut = findWaitingNodeOutput(nodeRecords);
    if (waitingOut != null) {
      interrupt = firstNonBlank(
          text(waitingOut, "interruptQuestion"),
          text(waitingOut, "message"),
          text(waitingOut, "prompt"));
    }
    if (interrupt != null && !interrupt.isBlank()) {
      if (sb.length() > 0) sb.append('\n');
      sb.append("## 待确认\n\n").append(interrupt.trim()).append('\n');
    } else if (waiting != null && waiting.prompt() != null && !waiting.prompt().isBlank()
        && sb.indexOf(waiting.prompt()) < 0) {
      if (sb.length() > 0) sb.append('\n');
      sb.append("## 待确认\n\n").append(waiting.prompt().trim()).append('\n');
    }
    if (waiting != null && waiting.fields() != null && !waiting.fields().isEmpty()) {
      boolean listed = sb.indexOf("## 需要你补充") >= 0;
      if (!listed) {
        sb.append("\n## 确认项\n\n");
        int i = 1;
        for (ExecutionStatusDto.FieldDto f : waiting.fields()) {
          if (f.label() == null || f.label().isBlank()) continue;
          sb.append(i++).append(". ").append(f.label().trim()).append('\n');
        }
      }
    }
    return sb.toString().trim();
  }

  private JsonNode findNodeOutput(JsonNode nodeRecords, String nodeType) {
    if (nodeRecords == null || !nodeRecords.isArray()) return null;
    JsonNode found = null;
    for (JsonNode n : nodeRecords) {
      if (nodeType.equalsIgnoreCase(text(n, "nodeType"))) {
        found = n.get("output");
      }
    }
    return found;
  }

  private JsonNode findWaitingNodeOutput(JsonNode nodeRecords) {
    if (nodeRecords == null || !nodeRecords.isArray()) return null;
    for (int i = nodeRecords.size() - 1; i >= 0; i--) {
      JsonNode n = nodeRecords.get(i);
      if ("waiting".equalsIgnoreCase(text(n, "status"))) {
        return n.get("output");
      }
    }
    return null;
  }

  private List<ExecutionStatusDto.FieldDto> extractAnalyzerQuestions(JsonNode nodeRecords) {
    JsonNode analyzer = findNodeOutput(nodeRecords, "requirement-analyzer");
    if (analyzer == null) return List.of();
    return mapConfirmQuestions(analyzer.get("confirmQuestions"));
  }

  private List<String> readQuestionTexts(JsonNode questions) {
    List<String> out = new ArrayList<>();
    if (questions == null || !questions.isArray()) return out;
    for (JsonNode q : questions) {
      if (q == null || q.isNull()) continue;
      if (q.isTextual()) {
        String t = q.asText().trim();
        if (!t.isBlank()) out.add(t);
      } else if (q.isObject()) {
        String t = firstNonBlank(text(q, "question"), text(q, "label"), text(q, "message"));
        if (t != null && !t.isBlank()) out.add(t.trim());
      }
    }
    return out;
  }

  /**
   * 平台 toExecution 常不带顶层 output；从 end / 末个成功节点提取可读文本。
   */
  private String extractNodeText(JsonNode nodeRecords) {
    if (nodeRecords == null || !nodeRecords.isArray() || nodeRecords.isEmpty()) return "";
    String endText = "";
    String lastSuccess = "";
    for (JsonNode n : nodeRecords) {
      String nodeType = text(n, "nodeType").toLowerCase();
      String nodeName = text(n, "nodeName");
      String status = text(n, "status").toLowerCase();
      String extracted = extractTextFromOutput(n.get("output"));
      if (extracted.isBlank()) continue;
      if ("success".equals(status) || "completed".equals(status)) {
        lastSuccess = extracted;
      }
      if (nodeType.contains("end") || "结束".equals(nodeName) || "end".equalsIgnoreCase(nodeName)) {
        endText = extracted;
      }
    }
    return firstNonBlank(endText, lastSuccess);
  }

  private String extractTextFromOutput(JsonNode output) {
    if (output == null || output.isNull()) return "";
    if (output.isTextual()) return output.asText();
    String direct = firstNonBlank(
        text(output, "text"),
        text(output, "message"),
        text(output, "content"),
        text(output, "answer"));
    if (direct != null && !direct.isBlank()) return direct;
    // 平台常见空壳 { "text": "" } —— 勿把 JSON 当用户可见正文
    if (output.isObject() && isEmptyTextEnvelope(output)) {
      return "";
    }
    try {
      return objectMapper.writeValueAsString(output);
    } catch (Exception e) {
      return output.toString();
    }
  }

  /** 仅含空 text/message/content 的输出壳，对用户无意义 */
  private boolean isEmptyTextEnvelope(JsonNode output) {
    if (!output.isObject() || output.isEmpty()) return true;
    int meaningful = 0;
    var it = output.fields();
    while (it.hasNext()) {
      var e = it.next();
      String key = e.getKey();
      JsonNode v = e.getValue();
      if (key.equals("text") || key.equals("message") || key.equals("content") || key.equals("answer")) {
        if (v != null && v.isTextual() && !v.asText().isBlank()) {
          meaningful++;
        }
        continue;
      }
      if (v != null && !v.isNull()) {
        if (v.isTextual() && v.asText().isBlank()) continue;
        if (v.isArray() && v.isEmpty()) continue;
        if (v.isObject() && v.isEmpty()) continue;
        meaningful++;
      }
    }
    return meaningful == 0;
  }

  private String streamingText(JsonNode streaming) {
    if (streaming == null || streaming.isNull()) return "";
    return text(streaming, "text");
  }

  private static String firstNonBlank(String... values) {
    if (values == null) return null;
    for (String v : values) {
      if (v != null && !v.isBlank()) return v;
    }
    return null;
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  /**
   * 解析 HITL 载荷。平台真实结构在 waiting 节点的 output（message + confirmQuestions），
   * 而非顶层 waiting 字段；兼容顶层 waiting，并在 WAITING_INPUT 时兜底默认审批动作。
   */
  private ExecutionStatusDto.WaitingPayloadDto resolveWaiting(
      ExecutionStatusDto.RunStatusDto status, JsonNode topLevelWaiting, JsonNode nodeRecords) {
    if (status != ExecutionStatusDto.RunStatusDto.WAITING_INPUT) {
      return parseWaitingNode(topLevelWaiting);
    }
    ExecutionStatusDto.WaitingPayloadDto fromTop = parseWaitingNode(topLevelWaiting);
    if (fromTop != null && hasUsableWaiting(fromTop)) {
      return ensureDefaultActions(fromTop);
    }
    ExecutionStatusDto.WaitingPayloadDto fromNodes = parseWaitingFromNodeRecords(nodeRecords);
    if (fromNodes != null) {
      return ensureDefaultActions(fromNodes);
    }
    return defaultWaitingPayload("智能体正在等待你的确认。");
  }

  private static boolean hasUsableWaiting(ExecutionStatusDto.WaitingPayloadDto waiting) {
    return (waiting.prompt() != null && !waiting.prompt().isBlank())
        || (waiting.actions() != null && !waiting.actions().isEmpty())
        || (waiting.fields() != null && !waiting.fields().isEmpty());
  }

  private ExecutionStatusDto.WaitingPayloadDto parseWaitingNode(JsonNode waiting) {
    if (waiting == null || waiting.isMissingNode() || waiting.isNull()) return null;
    String prompt = firstNonBlank(text(waiting, "prompt"), text(waiting, "message"));
    List<ExecutionStatusDto.FieldDto> fields = new ArrayList<>();
    JsonNode fieldsNode = waiting.get("fields");
    if (fieldsNode != null && fieldsNode.isArray()) {
      for (JsonNode f : fieldsNode) {
        fields.add(new ExecutionStatusDto.FieldDto(
            text(f, "key"), text(f, "label"), text(f, "type"), stringList(f, "options")));
      }
    }
    JsonNode questions = waiting.get("confirmQuestions");
    if (questions != null && questions.isArray()) {
      fields.addAll(mapConfirmQuestions(questions));
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
    if ((prompt == null || prompt.isBlank()) && fields.isEmpty() && actions.isEmpty()) {
      return null;
    }
    return new ExecutionStatusDto.WaitingPayloadDto(
        prompt == null ? "" : prompt, fields, actions, dangerous);
  }

  /**
   * 对齐平台 extractWorkflowWaitingHitl：从 status=waiting 的节点 output 取 message/confirmQuestions。
   */
  private ExecutionStatusDto.WaitingPayloadDto parseWaitingFromNodeRecords(JsonNode nodeRecords) {
    if (nodeRecords == null || !nodeRecords.isArray()) return null;
    for (JsonNode n : nodeRecords) {
      String nodeStatus = text(n, "status").toLowerCase();
      if (!"waiting".equals(nodeStatus)) continue;
      JsonNode output = n.get("output");
      String prompt = "";
      List<ExecutionStatusDto.FieldDto> fields = List.of();
      if (output != null && !output.isNull()) {
        prompt = firstNonBlank(
            text(output, "message"),
            text(output, "prompt"),
            text(output, "text"),
            text(output, "content"));
        if (prompt == null) prompt = "";
        JsonNode questions = output.get("confirmQuestions");
        if (questions != null && questions.isArray()) {
          fields = mapConfirmQuestions(questions);
        }
      }
      if (prompt.isBlank()) {
        String nodeName = firstNonBlank(text(n, "nodeName"), text(n, "nodeId"));
        prompt = nodeName == null || nodeName.isBlank()
            ? "智能体正在等待你的确认。"
            : "「" + nodeName + "」需要你的确认。";
      }
      return new ExecutionStatusDto.WaitingPayloadDto(prompt, fields, List.of(), false);
    }
    return null;
  }

  private List<ExecutionStatusDto.FieldDto> mapConfirmQuestions(JsonNode questions) {
    List<ExecutionStatusDto.FieldDto> fields = new ArrayList<>();
    if (questions == null || !questions.isArray()) return fields;
    int i = 0;
    for (JsonNode q : questions) {
      if (q == null || q.isNull()) continue;
      // 需求分析节点常见：字符串数组
      if (q.isTextual()) {
        String label = q.asText().trim();
        if (label.isBlank()) continue;
        String key = "aq" + (++i);
        fields.add(new ExecutionStatusDto.FieldDto(key, label, "textarea", List.of()));
        continue;
      }
      if (!q.isObject()) continue;
      String key = firstNonBlank(text(q, "id"), text(q, "key"), "q" + (++i));
      String label = firstNonBlank(text(q, "question"), text(q, "label"), key);
      List<String> options = stringList(q, "options");
      String type = options.isEmpty() ? "textarea" : "select";
      fields.add(new ExecutionStatusDto.FieldDto(key, label, type, options));
    }
    return fields;
  }

  private ExecutionStatusDto.WaitingPayloadDto ensureDefaultActions(
      ExecutionStatusDto.WaitingPayloadDto waiting) {
    if (waiting.actions() != null && !waiting.actions().isEmpty()) {
      return waiting;
    }
    return new ExecutionStatusDto.WaitingPayloadDto(
        waiting.prompt(),
        waiting.fields() == null ? List.of() : waiting.fields(),
        defaultActions(),
        waiting.dangerous());
  }

  private ExecutionStatusDto.WaitingPayloadDto defaultWaitingPayload(String prompt) {
    return new ExecutionStatusDto.WaitingPayloadDto(prompt, List.of(), defaultActions(), false);
  }

  private static List<ExecutionStatusDto.ActionDto> defaultActions() {
    return List.of(
        new ExecutionStatusDto.ActionDto("approve", "确认继续", "primary"),
        new ExecutionStatusDto.ActionDto("reject", "需要修改", "danger"));
  }

  /** 前端若把多字段答案序列化为 JSON，则转成平台 answers map。 */
  private Map<String, String> tryParseAnswers(String payload) {
    String trimmed = payload.trim();
    if (!trimmed.startsWith("{")) return null;
    try {
      JsonNode node = objectMapper.readTree(trimmed);
      if (node == null || !node.isObject()) return null;
      Map<String, String> answers = new java.util.LinkedHashMap<>();
      node.fields().forEachRemaining(e -> {
        if (e.getValue() != null && !e.getValue().isNull()) {
          answers.put(e.getKey(), e.getValue().asText());
        }
      });
      return answers.isEmpty() ? null : answers;
    } catch (Exception e) {
      return null;
    }
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
