package com.schemaplatform.workflowchat.service;

import com.schemaplatform.workflowchat.config.UploadProperties;
import com.schemaplatform.workflowchat.domain.ChatAttachment;
import com.schemaplatform.workflowchat.repository.ChatAttachmentRepository;
import com.schemaplatform.workflowchat.tenant.TenantContext;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 附件上传与读取。物理文件存 {@link UploadProperties#getRootDir()}。
 */
@Service
public class UploadService {

  private static final Logger log = LoggerFactory.getLogger(UploadService.class);

  private static final Set<String> ALLOWED_EXT = Set.of(
      "png", "jpg", "jpeg", "gif", "webp", "bmp",
      "pdf", "txt", "md", "csv", "json", "doc", "docx", "xls", "xlsx");

  private final UploadProperties props;
  private final ChatAttachmentRepository attachmentRepository;
  private Path root;

  public UploadService(UploadProperties props, ChatAttachmentRepository attachmentRepository) {
    this.props = props;
    this.attachmentRepository = attachmentRepository;
  }

  @PostConstruct
  void init() throws IOException {
    root = resolveRoot(props.getRootDir());
    Files.createDirectories(root);
    log.info("附件存储目录: {}", root.toAbsolutePath());
  }

  /**
   * 保存上传文件并写入元数据。
   */
  @Transactional
  public ChatAttachment store(MultipartFile file, String sessionId) {
    if (file == null || file.isEmpty()) {
      throw new IllegalArgumentException("请选择要上传的文件");
    }
    if (file.getSize() > props.getMaxFileBytes()) {
      throw new IllegalArgumentException("文件过大，单文件上限 "
          + (props.getMaxFileBytes() / (1024 * 1024)) + "MB");
    }
    String original = safeFilename(file.getOriginalFilename());
    String ext = extensionOf(original);
    if (!ALLOWED_EXT.contains(ext)) {
      throw new IllegalArgumentException("不支持的文件类型，请上传图片、PDF 或常见文档");
    }
    String contentType = file.getContentType();
    if (contentType == null || contentType.isBlank()) {
      contentType = guessContentType(ext);
    }

    String id = UUID.randomUUID().toString();
    String tenantId = TenantContext.tenantId();
    String userId = TenantContext.userId();
    String relative = tenantId + "/" + id + (ext.isEmpty() ? "" : "." + ext);
    Path target = root.resolve(relative).normalize();
    if (!target.startsWith(root)) {
      throw new IllegalStateException("非法存储路径");
    }
    try {
      Files.createDirectories(target.getParent());
      try (InputStream in = file.getInputStream()) {
        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (IOException e) {
      throw new IllegalStateException("文件保存失败，请稍后重试", e);
    }

    String excerpt = extractExcerpt(target, contentType, ext);
    ChatAttachment saved = attachmentRepository.save(ChatAttachment.create(
        id, tenantId, userId, blankToNull(sessionId),
        original, relative, contentType, file.getSize(), excerpt));
    log.info("附件已上传 id={} name={} size={} tenant={}", id, original, file.getSize(), tenantId);
    return saved;
  }

  @Transactional(readOnly = true)
  public ChatAttachment getOwned(String attachmentId) {
    return attachmentRepository.findByTenantIdAndId(TenantContext.tenantId(), attachmentId)
        .orElseThrow(() -> new java.util.NoSuchElementException("附件不存在"));
  }

  @Transactional(readOnly = true)
  public Resource openContent(String attachmentId) {
    ChatAttachment a = getOwned(attachmentId);
    Path path = root.resolve(a.getStoredRelativePath()).normalize();
    if (!path.startsWith(root) || !Files.isRegularFile(path)) {
      throw new java.util.NoSuchElementException("附件文件不存在");
    }
    return new FileSystemResource(path);
  }

  /**
   * 校验并绑定附件到消息；返回已绑定列表。
   */
  @Transactional
  public List<ChatAttachment> bindToMessage(String messageId, String sessionId, List<String> attachmentIds) {
    if (attachmentIds == null || attachmentIds.isEmpty()) {
      return List.of();
    }
    if (attachmentIds.size() > props.getMaxAttachmentsPerMessage()) {
      throw new IllegalArgumentException("单条消息最多 "
          + props.getMaxAttachmentsPerMessage() + " 个附件");
    }
    String tenantId = TenantContext.tenantId();
    String userId = TenantContext.userId();
    List<ChatAttachment> found = attachmentRepository.findByTenantIdAndIdIn(tenantId, attachmentIds);
    if (found.size() != attachmentIds.size()) {
      throw new IllegalArgumentException("部分附件不存在或无权使用");
    }
    List<ChatAttachment> bound = new ArrayList<>();
    for (ChatAttachment a : found) {
      if (!userId.equals(a.getUserId())) {
        throw new IllegalArgumentException("无权使用该附件");
      }
      if (a.getMessageId() != null && !a.getMessageId().isBlank()) {
        throw new IllegalArgumentException("附件已被使用: " + a.getOriginalFilename());
      }
      a.bindMessage(messageId, sessionId);
      bound.add(attachmentRepository.save(a));
    }
    return bound;
  }

  @Transactional(readOnly = true)
  public List<ChatAttachment> listByMessage(String messageId) {
    return attachmentRepository.findByTenantIdAndMessageIdOrderByCreatedAtAsc(
        TenantContext.tenantId(), messageId);
  }

  /** 拼到模型/Runtime 输入中的附件摘要。 */
  public static String formatAttachmentContext(List<ChatAttachment> attachments) {
    if (attachments == null || attachments.isEmpty()) return "";
    StringBuilder sb = new StringBuilder("\n\n[附件]\n");
    for (ChatAttachment a : attachments) {
      sb.append("- ").append(a.getOriginalFilename())
          .append(" (").append(a.getContentType()).append(", ")
          .append(a.getSizeBytes()).append(" bytes)");
      if (a.getExcerpt() != null && !a.getExcerpt().isBlank()) {
        sb.append("\n  摘录: ").append(a.getExcerpt().replace('\n', ' '));
      }
      sb.append('\n');
    }
    return sb.toString();
  }

  /**
   * 把已落盘附件转成平台 invoke 文件流（Base64）。
   * @param attachments 本轮附件
   * @return 平台 $input.files
   */
  public List<com.schemaplatform.workflowchat.runtime.RuntimeAdapter.InvokeFile> toInvokeFiles(
      List<ChatAttachment> attachments) {
    if (attachments == null || attachments.isEmpty()) return List.of();
    List<com.schemaplatform.workflowchat.runtime.RuntimeAdapter.InvokeFile> out = new ArrayList<>();
    for (ChatAttachment a : attachments) {
      try {
        Path path = root.resolve(a.getStoredRelativePath()).normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path)) continue;
        byte[] bytes = Files.readAllBytes(path);
        // 单附件上限与上传配置对齐，避免 invoke 体过大
        if (bytes.length > props.getMaxFileBytes()) {
          log.warn("附件过大跳过传平台 id={} size={}", a.getId(), bytes.length);
          continue;
        }
        String b64 = java.util.Base64.getEncoder().encodeToString(bytes);
        out.add(new com.schemaplatform.workflowchat.runtime.RuntimeAdapter.InvokeFile(
            a.getOriginalFilename(),
            a.getContentType() == null ? "application/octet-stream" : a.getContentType(),
            b64));
      } catch (Exception e) {
        log.warn("读取附件失败 id={}: {}", a.getId(), e.getMessage());
      }
    }
    return out;
  }

  static Path resolveRoot(String configured) {
    String value = configured == null || configured.isBlank() ? "~/payflow/agentChat" : configured.trim();
    if (value.startsWith("~/") || value.equals("~")) {
      String home = System.getProperty("user.home");
      value = value.equals("~") ? home : home + value.substring(1);
    }
    return Path.of(value).toAbsolutePath().normalize();
  }

  private static String safeFilename(String name) {
    if (name == null || name.isBlank()) return "file";
    String cleaned = name.replace('\\', '/');
    int slash = cleaned.lastIndexOf('/');
    if (slash >= 0) cleaned = cleaned.substring(slash + 1);
    cleaned = cleaned.replaceAll("[\\r\\n\\t]", "_").trim();
    return cleaned.isEmpty() ? "file" : cleaned;
  }

  private static String extensionOf(String filename) {
    int dot = filename.lastIndexOf('.');
    if (dot < 0 || dot == filename.length() - 1) return "";
    return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
  }

  private static String guessContentType(String ext) {
    return switch (ext) {
      case "png" -> "image/png";
      case "jpg", "jpeg" -> "image/jpeg";
      case "gif" -> "image/gif";
      case "webp" -> "image/webp";
      case "pdf" -> "application/pdf";
      case "txt", "md" -> "text/plain";
      case "csv" -> "text/csv";
      case "json" -> "application/json";
      case "doc" -> "application/msword";
      case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
      case "xls" -> "application/vnd.ms-excel";
      case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
      default -> "application/octet-stream";
    };
  }

  private static String extractExcerpt(Path path, String contentType, String ext) {
    boolean textLike = contentType.startsWith("text/")
        || "application/json".equals(contentType)
        || Set.of("txt", "md", "csv", "json").contains(ext);
    if (!textLike) return null;
    try {
      byte[] bytes = Files.readAllBytes(path);
      int max = Math.min(bytes.length, 4096);
      String text = new String(bytes, 0, max, StandardCharsets.UTF_8).trim();
      if (text.length() > 500) text = text.substring(0, 500) + "…";
      return text.isBlank() ? null : text;
    } catch (IOException e) {
      return null;
    }
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
