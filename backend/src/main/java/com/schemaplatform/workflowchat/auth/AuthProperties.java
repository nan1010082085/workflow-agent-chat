package com.schemaplatform.workflowchat.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 登录与 JWT 配置。与平台共用 JWT_SECRET，代理平台 /api/auth。
 */
@ConfigurationProperties(prefix = "chat.auth")
public class AuthProperties {

  /** 与 Schema Platform 相同的 JWT 签名密钥 */
  private String jwtSecret = "";
  /** 生产应 true：无有效 Bearer 拒绝业务 API */
  private boolean required = true;
  /** 本地联调：无 token 时允许 X-Tenant-Id / X-User-Id 兜底 */
  private boolean allowHeaderFallback = false;
  /** 平台鉴权基址；空则回退 runtime.base-url */
  private String platformBaseUrl = "";

  public String getJwtSecret() {
    return jwtSecret == null ? "" : jwtSecret;
  }

  public void setJwtSecret(String jwtSecret) {
    this.jwtSecret = jwtSecret;
  }

  public boolean isRequired() {
    return required;
  }

  public void setRequired(boolean required) {
    this.required = required;
  }

  public boolean isAllowHeaderFallback() {
    return allowHeaderFallback;
  }

  public void setAllowHeaderFallback(boolean allowHeaderFallback) {
    this.allowHeaderFallback = allowHeaderFallback;
  }

  public String getPlatformBaseUrl() {
    return platformBaseUrl == null ? "" : platformBaseUrl;
  }

  public void setPlatformBaseUrl(String platformBaseUrl) {
    this.platformBaseUrl = platformBaseUrl;
  }
}
