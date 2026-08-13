package com.schemaplatform.workflowchat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 租户+用户级 API 限流（内存令牌桶）。
 */
@ConfigurationProperties(prefix = "chat.rate-limit")
public class RateLimitProperties {

  private boolean enabled = true;

  /** 通用 API：每分钟请求数。 */
  private int apiPerMinute = 120;

  /** 上传：每分钟次数。 */
  private int uploadPerMinute = 20;

  /** 发消息 / 模型补全：每分钟次数。 */
  private int messagePerMinute = 30;

  public boolean isEnabled() { return enabled; }
  public void setEnabled(boolean enabled) { this.enabled = enabled; }
  public int getApiPerMinute() { return apiPerMinute; }
  public void setApiPerMinute(int apiPerMinute) { this.apiPerMinute = apiPerMinute; }
  public int getUploadPerMinute() { return uploadPerMinute; }
  public void setUploadPerMinute(int uploadPerMinute) { this.uploadPerMinute = uploadPerMinute; }
  public int getMessagePerMinute() { return messagePerMinute; }
  public void setMessagePerMinute(int messagePerMinute) { this.messagePerMinute = messagePerMinute; }
}
