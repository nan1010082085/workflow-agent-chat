-- 消息扩展字段：工具调用、文档摘要、工作流执行详情、提示信息
-- 存储为 JSON，由 RunSyncService 从 Runtime 同步时写入

ALTER TABLE chat_message
  ADD COLUMN tip TEXT NULL AFTER thinking,
  ADD COLUMN tool_calls JSON NULL AFTER tip,
  ADD COLUMN document_summaries JSON NULL AFTER tool_calls,
  ADD COLUMN workflow_execution JSON NULL AFTER document_summaries;
