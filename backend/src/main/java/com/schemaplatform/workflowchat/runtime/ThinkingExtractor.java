package com.schemaplatform.workflowchat.runtime;

/**
 * 从模型/Runtime 响应中提取 thinking 正文。
 * 支持独立字段，以及内容中的 &lt;think&gt; / &lt;thinking&gt; 包裹。
 */
public final class ThinkingExtractor {

  private ThinkingExtractor() {}

  /**
   * 若 content 内嵌思考标签，拆成 thinking + 剩余正文。
   */
  public static Split splitEmbedded(String content) {
    if (content == null || content.isBlank()) {
      return new Split("", content == null ? "" : content);
    }
    String thinking = extractTag(content, "think");
    if (thinking.isBlank()) thinking = extractTag(content, "thinking");
    if (thinking.isBlank()) {
      return new Split("", content);
    }
    String cleaned = content
        .replaceAll("(?s)<think>.*?</think>", "")
        .replaceAll("(?s)<thinking>.*?</thinking>", "")
        .trim();
    return new Split(thinking.trim(), cleaned);
  }

  private static String extractTag(String content, String tag) {
    String open = "<" + tag + ">";
    String close = "</" + tag + ">";
    int start = content.indexOf(open);
    if (start < 0) return "";
    int end = content.indexOf(close, start + open.length());
    if (end < 0) return "";
    return content.substring(start + open.length(), end);
  }

  public record Split(String thinking, String content) {}
}
