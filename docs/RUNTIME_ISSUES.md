# 执行层对接待确认问题清单（内部）

> **可见性**：本文档面向内部对接，含接口路径与认证设计讨论。若仓库为 Public，请评估是否迁出公开树或将仓库设为 Private。
>
> 记录 Chat 与上游执行层（Runtime / 平台 API）之间契约、凭证与能力待确认项。
>
> **原则**：Chat 只消费执行能力，不擅自改上游接口；对齐后再调整 adapter。

状态说明：`未确认` / `部分确认` / `已确认` / `已阻塞`

---

## 一、契约与认证前置（对应 TASKS C-01 / C-02 / C-03）

### ISS-01 Agent Catalog 契约未冻结 [未确认]

- **来源**：TASKS C-01；ARCHITECTURE §4
- **问题**：Chat 需要获取「当前租户可见、已发布的 Workflow Agent 列表」。架构文档指出过渡方案是「Chat Backend 读取平台管理 API 后过滤已发布 Workflow」，稳定方案需要平台新增只读聚合接口。
- **Chat 侧影响**：`AgentCatalogAdapter` 的接口路径、请求头、响应字段无法最终定型。
- **待确认项**：
  1. 稳定的 Catalog 接口路径与请求方法（是否为 `GET /api/ai/agents` 或平台新增聚合接口）。
  2. 响应字段集合：`agentId / slug / name / description / icon / supportedInputTypes / hitlCapable / version / updatedAt / publishedStatus` 的最小集合。
  3. 租户过滤语义：平台是否按 `X-Tenant-Id` 自动过滤，还是 Chat 侧需手动过滤。
  4. 草稿态与已发布态的区分字段。
  5. 分页/全量策略。
- **当前 Chat 侧处置**：Adapter 预留配置化 endpoint + 字段映射层，在契约冻结前以配置驱动，不硬编码字段语义。接口不可用时返回明确的可识别错误。

### ISS-02b Chat→Agent 多轮历史 [部分确认 / Chat 已补齐]

- **问题**：澄语每轮 invoke 仅传 `{ message }`，平台虽支持 `input.history` → `conversationHistory`，但 Chat 未传，导致智能体多轮「失忆」。
- **Chat 侧处置（2026-08-13）**：`ChatService` 组装近 20 轮 user/assistant 正文，经 `RuntimeRestAdapter` 写入 `input.history`；不含本轮 message。
- **平台侧处置**：`initExecutionConversation` 已消费 `history`；`agent-loop` 将 `conversationHistory` 注入 LLM 消息，并在结束后回写执行记录。
- **后续**：可评估 `continueFromExecutionId` 续跑链路（`triggeredBy` 归属需与 `X-Chat-Internal` 对齐）。

- **来源**：TASKS C-02；ARCHITECTURE §4 §5
- **问题**：架构文档列出了 Runtime 现有接口路径（`POST /api/ai/workflows/invoke/{slug}`、`GET .../executions/{id}`、`POST .../resume`、`POST .../cancel`），但请求体、响应体、状态枚举、错误码、超时、幂等键的精确契约未提供。
- **Chat 侧影响**：`RuntimeRestAdapter` 的请求/响应 DTO 与状态映射无法最终定型。
- **待确认项**：
  1. `invoke` 请求体字段（`input` / `messages` / `sessionId` / `tenantId` / `userId` / `idempotencyKey` 的实际集合）。
  2. `invoke` 响应体是否返回 `executionId`、`status`、`streamUrl`。
  3. `executions/{id}` 响应体字段：`status` 枚举精确值、`output` 结构、`error` 结构、`nodes/timeline` 结构、`waiting` 时的 `humanInputRequest` schema。
  4. `resume` 请求体：`action`(approve/reject/answer) + `payload` 的精确结构。
  5. `cancel` 是否需要请求体。
  6. 错误码与 HTTP 状态映射（404 未找到 execution、409 状态冲突、429 限流等）。
  7. 幂等键机制：Runtime 是否支持 `Idempotency-Key` 请求头，还是通过业务唯一键。
  8. 超时建议：invoke / status 轮询的推荐超时。
- **当前 Chat 侧处置**：Adapter 使用可配置的 DTO + 字段映射，状态映射按 ARCHITECTURE §5 的映射表实现，待 Runtime 契约冻结后对齐字段名。已预留 `RuntimeContractProperties` 配置类。

### ISS-03 认证与凭证链路未明确 [部分确认]

- **来源**：TASKS C-03；ARCHITECTURE §1 §1.2
- **问题**：PRD P0 要求「登录态接入，第一阶段支持 Chat Backend 代理现有 JWT」。架构要求「Runtime 的认证凭证只在后端配置，前端不持有 Workflow Key」。但用户 JWT → Chat Backend → Runtime 的具体凭证流转链路未确认。
- **Chat 侧影响**：无法确定 `TenantContextFilter` 如何解析租户、`RuntimeRestAdapter` 如何携带凭证。
- **已确认（2026-08-13）**：
  1. Catalog / model / **invoke / status / resume / cancel** 均可用服务凭证 `X-Chat-Internal`（与平台 `CHAT_INTERNAL_TOKEN` 对齐）。
  2. 第三方仍可用 `X-Workflow-Key` 或 `X-API-Key`；Chat BFF 不依赖 per-workflow invokeKey。
  3. Chat invoke 入参统一为 `{ message: string }`。
  4. **用户登录**：Chat 代理平台 `POST /api/auth/login`（及 refresh/me）；业务 API 校验平台 access JWT（共用 `JWT_SECRET`），`tenantId`/`userId` 取自 JWT，`chat_session` 按用户隔离。
- **仍待确认项**：
  1. SSO cookie 跨子路径共享是否需要（当前为 Chat 独立存 access/refresh）。
  2. 匿名/未登录访问是否允许（现默认 `CHAT_AUTH_REQUIRED=true` 禁止）。
- **当前 Chat 侧处置**：`TenantContextFilter` 解析 `Authorization: Bearer`；无 token 且未开 header fallback 时返回 401。凭证通过 `application.yml` / `.env` 配置，不进入前端构建产物。

---

## 二、Runtime 能力与行为待确认

### ISS-04 Runtime 实时通道：平台 Socket.IO（WS），非 SSE [已确认]

- **来源**：PRD 曾写「轮询；可替换 SSE」；平台实际能力为 Socket.IO
- **已确认（2026-08-13）**：
  1. 平台 AI 对话流式走 **Socket.IO**：`chat:send` / `chat:event` / `chat:cancel`（path 生产为 `/schema-platform/ws`）。
  2. 关键事件含 `thinking_delta`、`text_delta`、`done`、`error` 等；**不要**再以 HTTP SSE 作为澄语模型对话主路径。
  3. 澄语模型模式：前端持用户 JWT 直连平台 WS 流式渲染，结束后 `POST .../model-turns` 落库；同步 `POST .../completions` 仅作兜底。
  4. 助手（Workflow）执行进度平台另有 `workflow:*` WS；当前澄语助手模式仍可轮询 `executions/{id}`，后续可切 WS。
- **Chat 侧处置**：依赖平台 WS；文档与实现不再预留「SSE 替换轮询」为模型对话主方案。

---

### ISS-04b（历史）Runtime 轮询/SSE 能力与事件契约 [已替代]

> 原「待确认 SSE」条目已由上方 ISS-04 替代；助手路径轮询仍可用直至切 `workflow:*`。

原待确认项（归档）：
1. Runtime 是否提供 `GET .../executions/{id}/events` 或 SSE 端点 → **模型对话不采用 SSE**。
2. 轮询频率与终态保证 → 助手路径仍适用。

### ISS-05 HITL waiting 载荷 schema 未提供 [部分确认]

- **来源**：PRD §11 waiting 状态；UIUX §4 审批卡片；TASKS B-08
- **问题**：`waiting` 状态下 Runtime 返回的「审批/补充信息」载荷结构未提供，Chat 无法精确渲染 ApprovalCard 的字段、选项与动作。
- **Chat 侧影响**：`ApprovalCard` 组件与 `RuntimeRestAdapter.parseWaitingPayload` 的映射。
- **已确认（2026-08-13）**：
  1. 平台执行快照**没有**稳定的顶层 `waiting` 对象；HITL 信息在 `nodeRecords` 中 `status=waiting` 的节点 `output`。
  2. `output.message` 为提示文案；`output.confirmQuestions[]` 为 `{ id, question, options?, required? }`。
  3. `resume` body 为 `{ approved, comment?, answers? }`（Chat 已映射 approve/reject → approved）。
- **仍待确认项**：
  1. 是否还会提供顶层 `waiting` / `humanInputRequest` 聚合字段。
  2. 危险动作标记字段。
  3. 多轮 waiting（一次 run 多次等待）的幂等与消息关联。
- **当前 Chat 侧处置**：`RuntimeRestAdapter` 从 waiting 节点解析并映射为 `{prompt, fields, actions}`；缺动作时兜底「确认继续 / 拒绝」。

### ISS-06 Agent Catalog 与 Workflow 的多租户权限边界 [未确认]

- **来源**：PRD 验收标准 5；TASKS B-02 / B-04
- **问题**：用户通过 Chat 不应能读取其他租户的 Agent/Session/Run。Chat 侧已做 `tenant_id` 约束，但 Runtime 侧是否按 tenant 隔离 invoke 权限未明确。
- **Chat 侧影响**：跨租户访问的最终防线位置。
- **待确认项**：
  1. Runtime invoke 是否按 `X-Tenant-Id` 自动隔离，还是 Chat 必须先校验 agentId 属于当前租户。
  2. 跨租户 invoke 的错误表现。
- **当前 Chat 侧处置**：Chat 侧 Catalog adapter 与 send-message 闭环都强制 `tenant_id` 匹配校验，Runtime 侧隔离作为第二道防线期待。

---

## 三、工程与发布依赖

### ISS-07 开发环境 Runtime 地址与凭证 [未确认]

- **来源**：README 运行说明；application.yml
- **问题**：`RUNTIME_BASE_URL` 与 `RUNTIME_WORKFLOW_KEY` 的开发环境取值未提供。
- **Chat 侧处置**：配置项已就位，待环境就绪后填入。当前 workflow-key 为空，Adapter 在 key 为空时走 mock fallback 以保证前端联调。

### ISS-08 真实 Workflow 用于验收 [未确认]

- **来源**：TASKS 第一批工程任务 3、4；DEVELOPMENT_PLAN Phase 1
- **问题**：需要一个「已发布普通 Workflow」和一个「HITL Workflow」在测试环境跑通，但具体 workflow slug 与样例输入未提供。
- **Chat 侧处置**：前端 mock 数据保留两个示例 Agent（报销审核/文档摘要），待真实 workflow 就绪后替换。

---

## 更新记录

| 日期 | 更新内容 |
|---|---|
| 2026-08-13 | 初始建立，登记 ISS-01 ~ ISS-08 共 8 项待确认问题 |
| 2026-08-13 | 对齐新 TASKS P-05：ISS-01(Catalog 字段/租户过滤) + ISS-02(invoke/status/resume/cancel/错误码/幂等) + ISS-05(waiting payload) 共同构成 P-05 待确认集合；Chat 侧不擅自确定底层契约，待 Runtime 团队提供示例 JSON 与 contract fixture |
| 2026-08-13 | ISS-05 部分对齐：平台 HITL 实际在 `nodeRecords[status=waiting].output`（message + confirmQuestions）；Chat adapter 已按此解析并兜底 approve/reject；顶层 `waiting` 仍兼容 |
