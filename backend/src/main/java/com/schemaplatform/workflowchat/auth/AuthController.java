package com.schemaplatform.workflowchat.auth;

import com.schemaplatform.workflowchat.tenant.TenantContext;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Chat 登录代理：转发平台账号体系，本服务不存密码。
 */
@RestController
@RequestMapping("/api/chat/auth")
@Validated
public class AuthController {

  private final PlatformAuthClient platformAuthClient;

  public AuthController(PlatformAuthClient platformAuthClient) {
    this.platformAuthClient = platformAuthClient;
  }

  @PostMapping("/login")
  public Map<String, Object> login(@RequestBody @Validated LoginRequest req) {
    return platformAuthClient.login(req.username(), req.password(), req.tenantCode());
  }

  /**
   * 代理平台开放注册；成功后前端再调 login 拿 token。
   */
  @PostMapping("/register")
  public Map<String, Object> register(@RequestBody @Validated RegisterRequest req) {
    return platformAuthClient.register(
        req.username(), req.password(), req.displayName(), req.phone());
  }

  @PostMapping("/refresh")
  public Map<String, Object> refresh(@RequestBody @Validated RefreshRequest req) {
    return platformAuthClient.refresh(req.refreshToken());
  }

  @GetMapping("/me")
  public Map<String, Object> me() {
    AuthUser user = TenantContext.authUser();
    if (user == null) {
      throw new UnauthorizedException("未登录");
    }
    String token = TenantContext.accessToken();
    if (token != null && !token.isBlank()) {
      try {
        return platformAuthClient.me(token);
      } catch (Exception ignored) {
        // 平台 me 不可用时回退到 JWT 声明
      }
    }
    return Map.of(
        "id", user.id(),
        "username", user.username(),
        "displayName", user.username(),
        "tenantId", user.tenantId(),
        "roles", user.roles(),
        "deptId", user.deptId() == null ? "" : user.deptId());
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout() {
    // 客户端清 token；平台黑名单可选后续对接
    return ResponseEntity.noContent().build();
  }

  public record LoginRequest(
      @NotBlank String username,
      @NotBlank String password,
      String tenantCode
  ) {
  }

  public record RegisterRequest(
      @NotBlank String username,
      @NotBlank String password,
      String displayName,
      String phone
  ) {
  }

  public record RefreshRequest(@NotBlank String refreshToken) {
  }
}
