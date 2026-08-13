package com.schemaplatform.workflowchat.controller;

import com.schemaplatform.workflowchat.domain.ChatAttachment;
import com.schemaplatform.workflowchat.service.UploadService;
import java.time.Instant;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 附件上传与内容下载。
 */
@RestController
@RequestMapping("/api/chat/uploads")
public class UploadController {

  private final UploadService uploadService;

  public UploadController(UploadService uploadService) {
    this.uploadService = uploadService;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public AttachmentDto upload(
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "sessionId", required = false) String sessionId) {
    return AttachmentDto.from(uploadService.store(file, sessionId));
  }

  @GetMapping("/{id}")
  public AttachmentDto meta(@PathVariable String id) {
    return AttachmentDto.from(uploadService.getOwned(id));
  }

  @GetMapping("/{id}/content")
  public ResponseEntity<Resource> content(@PathVariable String id) {
    ChatAttachment meta = uploadService.getOwned(id);
    Resource body = uploadService.openContent(id);
    MediaType type;
    try {
      type = MediaType.parseMediaType(meta.getContentType());
    } catch (Exception e) {
      type = MediaType.APPLICATION_OCTET_STREAM;
    }
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION,
            "inline; filename=\"" + meta.getOriginalFilename().replace("\"", "") + "\"")
        .contentType(type)
        .body(body);
  }

  public record AttachmentDto(
      String id,
      String filename,
      String mimetype,
      long size,
      String excerpt,
      String url,
      Instant createdAt
  ) {
    public static AttachmentDto from(ChatAttachment a) {
      return new AttachmentDto(
          a.getId(),
          a.getOriginalFilename(),
          a.getContentType(),
          a.getSizeBytes(),
          a.getExcerpt(),
          "/api/chat/uploads/" + a.getId() + "/content",
          a.getCreatedAt());
    }
  }
}
