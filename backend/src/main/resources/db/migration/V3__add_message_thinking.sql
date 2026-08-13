-- 助手消息思考过程（对齐 ai/app thinking 字段）
ALTER TABLE chat_message
  ADD COLUMN thinking LONGTEXT NULL AFTER content;
