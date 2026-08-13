package com.schemaplatform.workflowchat.runtime;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 模型 Mock 适配器。RUNTIME_MOCK_ENABLED=true 时启用，供前端联调。
 */
public class ModelMockAdapter implements ModelAdapter {

  private static final Logger log = LoggerFactory.getLogger(ModelMockAdapter.class);

  private static final List<ModelDto> MOCK_MODELS = List.of(
      new ModelDto("mock-deepseek-v4", "DeepSeek V4 Flash", "deepseek-v4-flash",
          "DeepSeek", List.of("chat"), true),
      new ModelDto("mock-gpt-mini", "GPT Mini", "gpt-mini",
          "OpenAI", List.of("chat"), false)
  );

  @Override
  public ModelCatalog listModels(String tenantId) {
    log.debug("[MOCK] listModels tenant={}", tenantId);
    return new ModelCatalog(MOCK_MODELS, MOCK_MODELS.get(0).id());
  }

  @Override
  public CompletionResult complete(String tenantId, String modelId, List<Message> messages) {
    String lastUser = "";
    if (messages != null) {
      for (int i = messages.size() - 1; i >= 0; i--) {
        Message m = messages.get(i);
        if (m != null && "user".equalsIgnoreCase(m.role()) && m.content() != null) {
          lastUser = m.content().trim();
          break;
        }
      }
    }
    log.info("[MOCK] complete tenant={} model={} promptLen={}", tenantId, modelId, lastUser.length());
    if (lastUser.isEmpty()) {
      return new CompletionResult(
          "你好，我是本地联调模型。请输入一条消息试试。",
          "确认用户尚未提供具体任务，先给出引导回复。");
    }
    String snippet = lastUser.length() > 80 ? lastUser.substring(0, 80) + "…" : lastUser;
    String thinking = """
        1. 读取用户输入：「%s」
        2. 判断为普通对话请求
        3. 生成简短确认回复（mock）
        """.formatted(snippet).trim();
    String content = "（模拟回复）已收到：「" + snippet + "」。这是 Workflow Agent Chat 的本地 mock 模型响应。";
    return new CompletionResult(content, thinking);
  }
}
