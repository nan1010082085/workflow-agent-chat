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

### ISS-02 Runtime invoke/resume/cancel/status 契约字段未对齐 [未确认]

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

### ISS-04 Runtime 轮询/SSE 能力与事件契约 [未确认]

- **来源**：PRD P0「轮询适配器；统一设计为可替换 SSE」；P1「SSE 实时事件流」；TASKS O-05
- **问题**：当前 Runtime 是否已支持 SSE 事件流未知；轮询 `executions/{id}` 的频率上限与最终态保证未明确。
- **Chat 侧影响**：`RunStatusPoller` 的轮询间隔、退避策略、终态退出条件；未来 SSE 接入点。
- **待确认项**：
  1. Runtime 是否提供 `GET .../executions/{id}/events` 或 SSE 端点。
  2. SSE 事件类型集合（`node.start` / `node.finish` / `status.change` / `waiting` / `completed` / `error`）。
  3. 轮询频率建议与限流阈值。
  4. 最终态是否保证一定返回（是否会存在永驻 running）。
- **当前 Chat 侧处置**：先实现轮询，间隔可配置（默认 2s），带指数退避与最大轮询时长保护；SSE adapter 预留接口位，待 Runtime 支持后切换。

### ISS-05 HITL waiting 载荷 schema 未提供 [未确认]

- **来源**：PRD §11 waiting 状态；UIUX §4 审批卡片；TASKS B-08
- **问题**：`waiting` 状态下 Runtime 返回的「审批/补充信息」载荷结构未提供，Chat 无法精确渲染 ApprovalCard 的字段、选项与动作。
- **Chat 侧影响**：`ApprovalCard` 组件与 `RuntimeRestAdapter.parseWaitingPayload` 的映射。
- **待确认项**：
  1. waiting 载荷是否为 `{ type: 'approval'|'form'|'choice', prompt, fields[], actions[] }` 结构。
  2. 动作标识（approve/reject/answer）与 Runtime `resume` action 的对应。
  3. 是否支持多轮 waiting（一次 run 多次等待）。
  4. 危险动作的标记字段。
- **当前 Chat 侧处置**：Adapter 定义宽松的 `WaitingPayload` 解析，兼容未知字段；ApprovalCard 按 `{prompt, fields, actions}` 通用结构渲染，待 schema 冻结后细化。

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
