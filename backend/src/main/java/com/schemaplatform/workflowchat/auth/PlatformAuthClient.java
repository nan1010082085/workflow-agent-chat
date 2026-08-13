package com.schemaplatform.workflowchat.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

/**
 * 代理 Schema Platform 鉴权接口（login / refresh / me）。
 * 复用 runtime RestClient（base-url 指向平台）。
 */
@Service
public class PlatformAuthClient {

  private static final Logger log = LoggerFactory.getLogger(PlatformAuthClient.class);

  private final RestClient restClient;
  private final ObjectMapper objectMapper;

  public PlatformAuthClient(RestClient runtimeRestClient, ObjectMapper objectMapper) {
    this.restClient = runtimeRestClient;
    this.objectMapper = objectMapper;
  }

  public Map<String, Object> login(String username, String password, String tenantCode) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("username", username);
    body.put("password", password);
    if (tenantCode != null && !tenantCode.isBlank()) {
      body.put("tenantCode", tenantCode);
    }
    return exchange("POST", "/api/auth/login", body, null);
  }

  public Map<String, Object> refresh(String refreshToken) {
    return exchange("POST", "/api/auth/refresh", Map.of("refreshToken", refreshToken), null);
  }

  public Map<String, Object> register(String username, String password, String displayName, String phone) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("username", username);
    body.put("password", password);
    if (displayName != null && !displayName.isBlank()) {
      body.put("displayName", displayName);
    }
    if (phone != null && !phone.isBlank()) {
      body.put("phone", phone);
    }
    return exchange("POST", "/api/auth/register", body, null);
  }

  public Map<String, Object> me(String accessToken) {
    return exchange("GET", "/api/auth/me", null, accessToken);
  }

  private Map<String, Object> exchange(String method, String path, Object body, String bearer) {
    try {
      JsonNode node;
      if ("GET".equals(method)) {
        var spec = restClient.get().uri(path);
        if (bearer != null && !bearer.isBlank()) {
          spec = spec.header("Authorization", "Bearer " + bearer);
        }
        node = spec.retrieve().body(JsonNode.class);
      } else {
        var spec = restClient.post().uri(path).contentType(MediaType.APPLICATION_JSON);
        if (bearer != null && !bearer.isBlank()) {
          spec = spec.header("Authorization", "Bearer " + bearer);
        }
        node = spec.body(body == null ? Map.of() : body).retrieve().body(JsonNode.class);
      }
      return unwrap(node);
    } catch (HttpStatusCodeException e) {
      throw mapHttpError(e);
    } catch (UnauthorizedException e) {
      throw e;
    } catch (Exception e) {
      log.warn("平台鉴权调用失败 path={}: {}", path, e.getMessage());
      throw new UnauthorizedException("登录服务暂时不可用，请稍后重试");
    }
  }

  private RuntimeException mapHttpError(HttpStatusCodeException e) {
    int code = e.getStatusCode().value();
    String msg = "认证失败";
    try {
      JsonNode err = e.getResponseBodyAs(JsonNode.class);
      if (err != null && err.path("error").path("message").isTextual()) {
        msg = err.path("error").path("message").asText();
      }
    } catch (Exception ignored) {
      /* keep default */
    }
    if (code == 401 || code == 403) {
      return new UnauthorizedException(msg);
    }
    if (code == 400 || code == 409) {
      return new IllegalArgumentException(msg);
    }
    return new IllegalStateException(msg);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> unwrap(JsonNode node) {
    if (node == null) throw new UnauthorizedException("鉴权响应为空");
    if (node.has("success") && !node.get("success").asBoolean()) {
      String msg = node.path("error").path("message").asText("认证失败");
      throw new UnauthorizedException(msg);
    }
    JsonNode data = node.has("data") ? node.get("data") : node;
    return objectMapper.convertValue(data, Map.class);
  }
}
