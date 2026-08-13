package com.schemaplatform.workflowchat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;

/**
 * Runtime 适配配置。对应 application.yml 中 runtime.* 配置项。
 *
 * <p>注意：workflow-key 等凭证只存在后端配置，不暴露给前端。
 * 凭证链路待与平台对齐，见 docs/RUNTIME_ISSUES.md ISS-03。
 */
@ConfigurationProperties(prefix = "runtime")
@ConfigurationPropertiesBinding
public record RuntimeProperties(
    String baseUrl,
    String workflowKey,
    String apiKey,
    String catalogPath,
    String invokePathTemplate,
    String executionPathTemplate,
    String resumePathTemplate,
    String cancelPathTemplate,
    int connectTimeoutMs,
    int readTimeoutMs,
    int pollIntervalMs,
    int pollMaxDurationMs,
    boolean mockEnabled
) {
  public RuntimeProperties {
    if (baseUrl == null || baseUrl.isBlank()) {
      baseUrl = "http://localhost:3001";
    }
    if (catalogPath == null || catalogPath.isBlank()) {
      catalogPath = "/api/ai/agents";
    }
    if (invokePathTemplate == null || invokePathTemplate.isBlank()) {
      invokePathTemplate = "/api/ai/workflows/invoke/{slug}";
    }
    if (executionPathTemplate == null || executionPathTemplate.isBlank()) {
      executionPathTemplate = "/api/ai/workflows/invoke/executions/{id}";
    }
    if (resumePathTemplate == null || resumePathTemplate.isBlank()) {
      resumePathTemplate = "/api/ai/workflows/invoke/executions/{id}/resume";
    }
    if (cancelPathTemplate == null || cancelPathTemplate.isBlank()) {
      cancelPathTemplate = "/api/ai/workflows/invoke/executions/{id}/cancel";
    }
    if (connectTimeoutMs <= 0) {
      connectTimeoutMs = 5000;
    }
    if (readTimeoutMs <= 0) {
      readTimeoutMs = 30000;
    }
    if (pollIntervalMs <= 0) {
      pollIntervalMs = 2000;
    }
    if (pollMaxDurationMs <= 0) {
      pollMaxDurationMs = 300000;
    }
  }

  /**
   * workflow-key 为空或 mockEnabled=true 时，Adapter 走 mock fallback。
   * 用于前端联调阶段，避免强依赖 Runtime。见 ISS-07。
   */
  public boolean useMock() {
    return mockEnabled || ((workflowKey == null || workflowKey.isBlank())
        && (apiKey == null || apiKey.isBlank()));
  }

  /** Platform invoke accepts X-Workflow-Key or X-API-Key, never Bearer. */
  public String credentialHeader() {
    String key = executionCredential();
    return key != null && (key.startsWith("sk_") || key.startsWith("sk-"))
        ? "X-API-Key" : "X-Workflow-Key";
  }

  public String executionCredential() {
    return apiKey != null && !apiKey.isBlank() ? apiKey : workflowKey;
  }

  public String catalogCredential() {
    return apiKey != null && !apiKey.isBlank() ? apiKey : workflowKey;
  }

  public String catalogCredentialHeader() {
    String key = catalogCredential();
    return key != null && (key.startsWith("sk_") || key.startsWith("sk-"))
        ? "X-API-Key" : "X-Workflow-Key";
  }
}
