package com.schemaplatform.workflowchat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 上传存储与校验配置。默认落盘目录：~/payflow/agentChat。
 */
@ConfigurationProperties(prefix = "chat.upload")
public class UploadProperties {

  /** 物理根目录；支持 ~ 展开。Docker 内建议 /data/agentChat。 */
  private String rootDir = "~/payflow/agentChat";

  /** 单文件最大字节数。 */
  private long maxFileBytes = 10L * 1024 * 1024;

  /** 单次消息最多附件数。 */
  private int maxAttachmentsPerMessage = 5;

  public String getRootDir() { return rootDir; }
  public void setRootDir(String rootDir) { this.rootDir = rootDir; }
  public long getMaxFileBytes() { return maxFileBytes; }
  public void setMaxFileBytes(long maxFileBytes) { this.maxFileBytes = maxFileBytes; }
  public int getMaxAttachmentsPerMessage() { return maxAttachmentsPerMessage; }
  public void setMaxAttachmentsPerMessage(int maxAttachmentsPerMessage) {
    this.maxAttachmentsPerMessage = maxAttachmentsPerMessage;
  }
}
