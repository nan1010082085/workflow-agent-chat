# 技术架构与 API 契约

## 1. 部署拓扑

```text
Chat Web
    |
    v
Chat Backend  ---- MySQL 8
    |
    | 租户头 + 服务端凭证
    v
外部 Agent / 模型 Runtime
    |
    +-- 已发布助手目录
    +-- 执行与状态
    +-- 人工确认 / 回调
```

Chat Backend 是 BFF 与产品领域服务，不直接读取 Runtime 业务库。执行层凭证只在后端配置。

## 1.1 框架选择

新 Chat 项目本身不使用 LangChain 或 LangGraph 作为核心架构。

- Vue 3 + TypeScript：Chat 产品界面。
- Spring Boot：Chat BFF、会话、消息、租户、权限、Runtime adapter、SSE。
- MySQL：Chat 产品数据。
- 现有 AI Runtime：继续使用 LangGraph、Workflow Executor、BullMQ、MCP 和模型配置。

LangGraph 属于 Agent Runtime，不属于 Chat 消费端。Chat 只有在未来需要“自动拆任务、调用多个 Workflow、合并结果、跨多个审批点恢复”时，才应在 Runtime 增加 Orchestrator 能力；不在 Java Chat Backend 内复制一套图。

## 1.2 服务职责

| 服务 | 负责 | 不负责 |
|---|---|---|
| Chat Web | 会话交互、Agent 选择、结果展示 | 保存密钥、执行 Workflow |
| Chat Backend | Chat 数据、权限、Runtime BFF、SSE/轮询 | 编辑/发布 Workflow、直接调用模型 |
| AI Runtime | Agent Catalog 来源、Workflow 执行、模型/工具/RAG、HITL | Chat UI、Chat 会话列表 |
| MySQL | session/message/run 映射 | Workflow graph 和 Runtime execution 明细 |

## 2. 数据模型

### chat_session

`id, tenant_id, user_id, title, agent_id, agent_name_snapshot, status, created_at, updated_at`

### chat_message

`id, tenant_id, session_id, role, content, runtime_execution_id, status, created_at`

### chat_run

`id, tenant_id, session_id, agent_id, runtime_execution_id, status, error_message, started_at, finished_at`

所有业务查询必须带 `tenant_id`。Runtime 版本和 Agent 名称在 Chat 侧做快照，避免历史会话随 Agent 改名而失真。

## 3. Chat API

```text
GET  /api/chat/agents
POST /api/chat/sessions
GET  /api/chat/sessions
GET  /api/chat/sessions/{id}/messages
POST /api/chat/sessions/{id}/messages
GET  /api/chat/runs/{id}
POST /api/chat/runs/{id}/resume
POST /api/chat/runs/{id}/cancel
```

`POST /messages` 请求：

```json
{ "agentId": "expense-audit", "content": "审核这张报销单" }
```

响应：

```json
{ "messageId": "...", "runId": "...", "status": "running" }
```

## 4. Runtime 适配

当前适配现有接口：

```text
POST /api/ai/workflows/invoke/{slug}
GET  /api/ai/workflows/invoke/executions/{id}
POST /api/ai/workflows/invoke/executions/{id}/resume
POST /api/ai/workflows/invoke/executions/{id}/cancel
```

Agent Catalog 的第一版可由平台增加一个只读聚合接口；临时过渡方案是 Chat Backend 读取平台管理 API 后过滤已发布 Workflow。该过渡接口必须在开发阶段替换为稳定的 catalog contract。

## 5. 状态映射

```text
Runtime running   -> Chat RUNNING
Runtime success   -> Chat COMPLETED
Runtime error     -> Chat FAILED
Runtime waiting   -> Chat WAITING_INPUT
Runtime cancelled -> Chat CANCELLED
```

## 6. 技术决策

- Java Spring Boot：承载认证、租户隔离、Chat 数据、Runtime adapter、SSE 聚合。
- MySQL：只存 Chat 产品自有数据，使用 Flyway 管理 schema。
- Vue 3 + TypeScript：独立 Chat UI；不复制 `ai/app` 的 Pinia/组件。
- 初期轮询：兼容当前 Runtime；Runtime 事件稳定后切换 SSE，前端 API 不变。
- 不引入 LangGraph：Chat 的职责是消费 Workflow，不负责重新编排底层 Agent。
