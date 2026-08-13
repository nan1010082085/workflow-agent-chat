-- 模型模式对接平台 LangGraph WS 时，复用平台 AIConversation（Mongo ObjectId）
ALTER TABLE chat_session
  ADD COLUMN platform_conversation_id VARCHAR(64) NULL;
