# Workflow Agent Chat API 接口文档

> 基础路径: `/api/chat`
>
> 认证方式: JWT Bearer Token（通过 `Authorization` Header 传递）
>
> 内容类型: `application/json`（除文件上传外）

---

## 目录

- [1. 认证接口 (Auth)](#1-认证接口-auth)
- [2. 会话接口 (Session)](#2-会话接口-session)
- [3. 消息接口 (Message)](#3-消息接口-message)
- [4. 助手接口 (Agent)](#4-助手接口-agent)
- [5. 模型接口 (Model)](#5-模型接口-model)
- [6. 运行接口 (Run)](#6-运行接口-run)
- [7. 上传接口 (Upload)](#7-上传接口-upload)
- [8. 健康检查 (Health)](#8-健康检查-health)
- [附录: 数据模型](#附录-数据模型)

---

## 1. 认证接口 (Auth)

### 1.1 用户登录

代理平台账号体系登录，本服务不存储密码。

```
POST /api/chat/auth/login
```

**请求体:**

```json
{
  "username": "string (必填)",
  "password": "string (必填)",
  "tenantCode": "string (可选)"
}
```

**响应:**

```json
{
  "accessToken": "string",
  "refreshToken": "string",
  "expiresIn": 3600,
  "user": {
    "id": "string",
    "username": "string",
    "displayName": "string"
  }
}
```

---

### 1.2 用户注册

代理平台开放注册，成功后需调用 `login` 获取 token。

```
POST /api/chat/auth/register
```

**请求体:**

```json
{
  "username": "string (必填)",
  "password": "string (必填)",
  "displayName": "string (可选)",
  "phone": "string (可选)"
}
```

**响应:**

```json
{
  "success": true,
  "message": "注册成功"
}
```

---

### 1.3 刷新 Token

```
POST /api/chat/auth/refresh
```

**请求体:**

```json
{
  "refreshToken": "string (必填)"
}
```

**响应:**

```json
{
  "accessToken": "string",
  "refreshToken": "string",
  "expiresIn": 3600
}
```

---

### 1.4 获取当前用户信息

```
GET /api/chat/auth/me
```

**响应:**

```json
{
  "id": "string",
  "username": "string",
  "displayName": "string",
  "tenantId": "string",
  "roles": ["string"],
  "deptId": "string"
}
```

---

### 1.5 退出登录

```
POST /api/chat/auth/logout
```

**响应:** `204 No Content`

---

## 2. 会话接口 (Session)

### 2.1 获取会话列表

获取当前用户最近的会话列表。

```
GET /api/chat/sessions
```

**响应:**

```json
[
  {
    "id": "string",
    "title": "string",
    "agentId": "string",
    "agentName": "string",
    "platformConversationId": "string",
    "status": "ACTIVE | ARCHIVED",
    "createdAt": "2026-08-14T10:00:00Z",
    "updatedAt": "2026-08-14T10:00:00Z"
  }
]
```

---

### 2.2 创建会话

```
POST /api/chat/sessions
```

**请求体:**

```json
{
  "title": "string (可选，默认根据 agentName 生成)",
  "agentId": "string (可选)",
  "agentName": "string (可选)"
}
```

**响应:**

```json
{
  "id": "string",
  "title": "string",
  "agentId": "string",
  "agentName": "string",
  "platformConversationId": null,
  "status": "ACTIVE",
  "createdAt": "2026-08-14T10:00:00Z",
  "updatedAt": "2026-08-14T10:00:00Z"
}
```

---

### 2.3 更新会话标题

```
PATCH /api/chat/sessions/{sessionId}/title
```

**路径参数:**

| 参数 | 类型 | 说明 |
|------|------|------|
| sessionId | string | 会话 ID |

**请求体:**

```json
{
  "title": "string (必填)"
}
```

**响应:** 同会话摘要结构

---

## 3. 消息接口 (Message)

### 3.1 获取会话消息列表

```
GET /api/chat/sessions/{sessionId}/messages
```

**路径参数:**

| 参数 | 类型 | 说明 |
|------|------|------|
| sessionId | string | 会话 ID |

**响应:**

```json
[
  {
    "id": "string",
    "role": "user | assistant",
    "content": "string",
    "thinking": "string (助手思考过程，可为 null)",
    "runtimeExecutionId": "string (可为 null)",
    "status": "PENDING | RUNNING | COMPLETED | FAILED | CANCELLED",
    "createdAt": "2026-08-14T10:00:00Z",
    "attachments": [
      {
        "id": "string",
        "filename": "string",
        "mimetype": "string",
        "size": 1024,
        "excerpt": "string",
        "url": "/api/chat/uploads/{id}/content",
        "createdAt": "2026-08-14T10:00:00Z"
      }
    ]
  }
]
```

---

### 3.2 发送消息（助手模式）

发送消息并触发 Workflow Runtime 执行。

```
POST /api/chat/sessions/{sessionId}/messages
```

**路径参数:**

| 参数 | 类型 | 说明 |
|------|------|------|
| sessionId | string | 会话 ID |

**请求体:**

```json
{
  "agentId": "string (必填)",
  "content": "string (可选)",
  "attachmentIds": ["string"] // 可选，上传附件的 ID 列表
}
```

**响应:**

```json
{
  "userMessage": { /* MessageDto */ },
  "assistantMessage": { /* MessageDto */ },
  "executionId": "string",
  "status": "RUNNING | COMPLETED | FAILED"
}
```

---

### 3.3 模型对话（同步补全）

基础模型同步对话并落库。

```
POST /api/chat/sessions/{sessionId}/completions
```

**路径参数:**

| 参数 | 类型 | 说明 |
|------|------|------|
| sessionId | string | 会话 ID |

**请求体:**

```json
{
  "modelId": "string (必填)",
  "content": "string (可选)",
  "attachmentIds": ["string"] // 可选
}
```

**响应:**

```json
{
  "userMessage": { /* MessageDto */ },
  "assistantMessage": { /* MessageDto */ },
  "content": "string",
  "thinking": "string"
}
```

---

### 3.4 持久化流式模型回合

持久化前端经平台 WebSocket 流式得到的模型回合，不调用 LLM。

```
POST /api/chat/sessions/{sessionId}/model-turns
```

**路径参数:**

| 参数 | 类型 | 说明 |
|------|------|------|
| sessionId | string | 会话 ID |

**请求体:**

```json
{
  "modelId": "string (必填)",
  "content": "string (可选)",
  "attachmentIds": ["string"],
  "assistantContent": "string (助手回复内容)",
  "thinking": "string (思考过程，可选)",
  "platformConversationId": "string (平台会话 ID，可选)",
  "status": "COMPLETED | FAILED | CANCELLED (可选，默认 COMPLETED)"
}
```

**响应:** 同 3.3

---

## 4. 助手接口 (Agent)

### 4.1 获取助手目录

获取当前租户可见、已发布的 Workflow Agent 列表。

```
GET /api/chat/agents
```

**响应:**

```json
[
  {
    "id": "string",
    "slug": "string",
    "name": "string",
    "description": "string",
    "icon": "string (emoji 或图标标识)",
    "supportedInputs": ["text", "file", "image"],
    "hitlCapable": true,
    "version": "string",
    "updatedAt": "2026-08-14T10:00:00Z",
    "published": true
  }
]
```

**supportedInputs 说明:**

| 值 | 说明 |
|------|------|
| text | 纯文本输入 |
| file | 文件附件（PDF、Word、Excel 等）|
| image | 图片附件 |

---

## 5. 模型接口 (Model)

### 5.1 获取模型列表

```
GET /api/chat/models
```

**响应:**

```json
{
  "models": [
    {
      "id": "string",
      "name": "string",
      "provider": "string",
      "description": "string"
    }
  ]
}
```

---

### 5.2 模型同步补全

直接调用模型进行对话补全（不经过会话持久化）。

```
POST /api/chat/models/completions
```

**请求体:**

```json
{
  "modelId": "string (必填)",
  "messages": [
    {
      "role": "system | user | assistant",
      "content": "string"
    }
  ]
}
```

**响应:**

```json
{
  "modelId": "string",
  "content": "string (助手回复)",
  "thinking": "string (思考过程，可为 null)"
}
```

---

## 6. 运行接口 (Run)

### 6.1 通过执行 ID 查询运行状态

```
GET /api/chat/runs/by-execution/{executionId}
```

**路径参数:**

| 参数 | 类型 | 说明 |
|------|------|------|
| executionId | string | Runtime 执行 ID |

**响应:**

```json
{
  "runId": "string",
  "executionId": "string",
  "status": "PENDING | RUNNING | COMPLETED | FAILED | WAITING_INPUT | CANCELLED",
  "output": "string",
  "error": "string (失败时)",
  "waitingFields": [
    {
      "fieldId": "string",
      "label": "string",
      "type": "text | select | confirm",
      "options": ["string"],
      "required": true
    }
  ],
  "startedAt": "2026-08-14T10:00:00Z",
  "completedAt": "2026-08-14T10:00:00Z"
}
```

---

### 6.2 通过运行 ID 查询状态

```
GET /api/chat/runs/{runId}
```

**路径参数:**

| 参数 | 类型 | 说明 |
|------|------|------|
| runId | string | 运行 ID |

**响应:** 同 6.1

---

### 6.3 HITL 恢复运行

当运行状态为 `WAITING_INPUT` 时，提交用户输入以继续执行。

```
POST /api/chat/runs/{runId}/resume
```

**路径参数:**

| 参数 | 类型 | 说明 |
|------|------|------|
| runId | string | 运行 ID |

**请求体:**

```json
{
  "action": "string (必填，如 approve | reject | provide_info)",
  "payload": "string (可选，用户输入内容)"
}
```

**响应:** 同 6.1

---

### 6.4 取消运行

```
POST /api/chat/runs/{runId}/cancel
```

**路径参数:**

| 参数 | 类型 | 说明 |
|------|------|------|
| runId | string | 运行 ID |

**响应:** 同 6.1

---

## 7. 上传接口 (Upload)

### 7.1 上传文件

```
POST /api/chat/uploads
Content-Type: multipart/form-data
```

**请求参数:**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| file | File | 是 | 文件内容 |
| sessionId | string | 否 | 关联的会话 ID |

**支持的文件类型:**

| 类型 | MIME |
|------|------|
| 文本 | text/plain, text/csv |
| 文档 | application/msword, application/vnd.openxmlformats-officedocument.wordprocessingml.document |
| 表格 | application/vnd.ms-excel, application/vnd.openxmlformats-officedocument.spreadsheetml.sheet |
| PDF | application/pdf |
| 图片 | image/png, image/jpeg, image/gif, image/webp |
| 数据 | application/json |

**响应:**

```json
{
  "id": "string",
  "filename": "string",
  "mimetype": "string",
  "size": 1024,
  "excerpt": "string (文本文件摘录)",
  "url": "/api/chat/uploads/{id}/content",
  "createdAt": "2026-08-14T10:00:00Z"
}
```

---

### 7.2 获取附件元信息

```
GET /api/chat/uploads/{id}
```

**路径参数:**

| 参数 | 类型 | 说明 |
|------|------|------|
| id | string | 附件 ID |

**响应:** 同 7.1

---

### 7.3 下载/预览附件内容

```
GET /api/chat/uploads/{id}/content
```

**路径参数:**

| 参数 | 类型 | 说明 |
|------|------|------|
| id | string | 附件 ID |

**响应:** 文件流（Content-Type 根据文件类型设置，Content-Disposition: inline）

---

## 8. 健康检查 (Health)

### 8.1 服务健康检查

```
GET /actuator-lite/health
```

**响应:**

```json
{
  "status": "UP",
  "service": "workflow-agent-chat"
}
```

---

## 附录: 数据模型

### MessageStatus (消息状态)

| 值 | 说明 |
|------|------|
| PENDING | 排队中 |
| RUNNING | 处理中 |
| COMPLETED | 已完成 |
| FAILED | 失败 |
| CANCELLED | 已取消 |
| WAITING_INPUT | 等待用户输入（HITL）|

### SessionStatus (会话状态)

| 值 | 说明 |
|------|------|
| ACTIVE | 活跃 |
| ARCHIVED | 已归档 |

### Role (消息角色)

| 值 | 说明 |
|------|------|
| user | 用户消息 |
| assistant | 助手回复 |
| system | 系统消息 |

---

## 错误响应

所有接口在出错时返回统一格式：

```json
{
  "error": "string (错误类型)",
  "message": "string (错误描述)",
  "timestamp": "2026-08-14T10:00:00Z"
}
```

**常见 HTTP 状态码:**

| 状态码 | 说明 |
|------|------|
| 200 | 成功 |
| 201 | 创建成功 |
| 204 | 成功（无内容）|
| 400 | 请求参数错误 |
| 401 | 未认证 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 413 | 文件过大 |
| 500 | 服务器内部错误 |

---

## WebSocket 接口

### 平台消息推送

```
WS /api/chat/ws
```

**连接参数:**

| 参数 | 类型 | 说明 |
|------|------|------|
| token | string | JWT 认证令牌 |

**消息格式:**

```json
{
  "type": "message | status | error",
  "payload": { /* 具体数据 */ }
}
```

---

## 认证说明

除以下接口外，所有 API 需要在请求 Header 中携带 JWT Token：

- `GET /actuator-lite/health` - 健康检查
- `POST /api/chat/auth/login` - 登录
- `POST /api/chat/auth/register` - 注册

**Header 格式:**

```
Authorization: Bearer <your-jwt-token>
```

---

*文档生成时间: 2026-08-14*
*基于代码版本: main branch @ dc38d65*
