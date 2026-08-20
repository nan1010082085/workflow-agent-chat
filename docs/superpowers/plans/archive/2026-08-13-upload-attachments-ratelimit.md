# 文件上传、附件展示与限流 Implementation Plan

> **For agentic workers:** 按任务勾选；本计划在同会话落地。

**Goal:** 打通 Composer 文件/图片上传与消息侧展示；文件落盘到服务器 `~/payflow/agentChat`；对上传与发消息做租户+用户级限流。

**Architecture:** 上传先落盘并建 `chat_attachment` 记录；发送消息时把 `attachmentIds` 挂到 user message；消息列表 DTO 带回 attachments；下载走鉴权流接口。限流用内存令牌桶 Filter（无新中间件依赖）。

**Tech Stack:** Spring Boot Multipart + Flyway；Vue Composer FormData；Docker volume 挂载宿主机目录。

**Spec:** PRD P1 文件附件、TASKS O-06 / O-02 限流；存储路径按产品要求 `~/payflow/agentChat`。

## Global Constraints

- 用户文案：助手 / 对话 / 处理状态；不暴露 Runtime 术语
- 单文件 ≤ 10MB；白名单 MIME/扩展名
- Docker 内路径 `/data/agentChat`，宿主机 `CHAT_UPLOAD_HOST_PATH`（默认 `/home/ubuntu/payflow/agentChat`）

---

## 文件地图

| 文件 | 职责 |
|---|---|
| `V4__add_chat_attachment.sql` | 附件表 |
| `domain/ChatAttachment.java` | 实体 |
| `service/UploadService.java` | 存盘、校验、关联消息 |
| `controller/UploadController.java` | 上传/下载 |
| `config/RateLimit*` | 限流配置与 Filter |
| `MessageController` / `ChatService` | 发送带 attachmentIds |
| `Composer.vue` / `MessageAttachmentList.vue` | 选文件、展示 |
| `docker-compose.yml` / `.env.example` | volume + 环境变量 |

---

### Task 1：库表与配置

- [x] Flyway V4 `chat_attachment`
- [x] `chat.upload.*` / `chat.rate-limit.*` 配置项
- [x] Docker volume + 部署目录创建

### Task 2：上传/下载后端

- [x] UploadService（`~` 展开、校验、落盘）
- [x] POST `/api/chat/uploads`、GET `/api/chat/uploads/{id}/content`
- [x] 关联到 message

### Task 3：消息闭环

- [x] sendMessage / completeModelTurn 接受 attachmentIds
- [x] MessageDto 返回 attachments
- [x] invoke/complete 输入附带附件摘要

### Task 4：限流

- [x] 按 tenant+user 对 upload / message / api 分桶
- [x] 超限 429 `RATE_LIMITED`

### Task 5：前端

- [x] Composer 真实上传 + pending 列表
- [x] 消息气泡展示图片预览与文件卡
- [x] API client FormData；429 文案

### Task 6：验收

- [x] backend compile + frontend build
- [x] 计划勾选与索引更新

---

## 范围外

| 项 | 说明 |
|---|---|
| SSE | 仍走 O-05 |
| PDF/Office 全文抽取 | 仅存文件 + 文本类 excerpt |
| 自动路由 / 收藏搜索 | O-06 其余子项 |
