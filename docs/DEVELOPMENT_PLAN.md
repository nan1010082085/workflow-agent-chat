# 开发计划

## Phase 0：契约与环境（1-2 天）

- 确认 Runtime catalog、invoke、resume、cancel 的生产契约。
- 确认认证方式：用户 JWT 如何换取/代理 Runtime 调用凭证。
- 确认租户、用户与 Runtime workflow 的权限映射。
- 建立 MySQL、Flyway、CI、开发环境配置。
- 确认 Agent Catalog 字段：名称、描述、图标、输入能力、HITL 能力、版本。

## Phase 0.5：UI/UX 定稿（1-2 天）

- 根据 `docs/UIUX.md` 在 Figma 或等效工具中制作桌面/移动端高保真稿。
- 先验证三栏工作台、Agent 选择、执行详情、waiting 审批四个核心状态。
- 设计组件状态表：hover、focus、disabled、loading、error、empty、long text。
- 用真实 Workflow 结果替换假数据验证消息、表格、结构化输出和时间线布局。

## Phase 1：可用闭环（3-5 天）

- Spring Boot 基础工程、统一错误响应、租户上下文。
- `chat_session/message/run` 表及 Repository/Service。
- Runtime REST client 与状态轮询。
- Agent 列表、会话列表、消息发送。
- Vue Chat 主界面、Agent 选择器、消息渲染。

## Phase 2：可靠性与人工介入（2-4 天）

- waiting 状态与审批组件。
- resume/cancel。
- 幂等键、超时、重试、Runtime 不可用处理。
- 刷新恢复、断线重连、错误日志与审计。

## Phase 3：体验升级（3-5 天）

- 模型对话：平台 Socket.IO 事件流；助手执行进度可演进 `workflow:*`。
- 文件附件。
- Markdown、代码、结构化输出渲染。
- 自动 Agent 路由和 Agent 搜索。

## Phase 4：生产化（3-5 天）

- 权限、限流、配额、成本统计。
- 监控指标：首响应、完成耗时、成功率、Runtime 错误率。
- 集成测试、E2E、灰度发布、备份与数据保留策略。

## 第一批工程任务

1. 建立 Runtime contract test，防止 Chat 与平台接口漂移。
2. 完成租户隔离测试，覆盖 session、message、run、agent。
3. 用一个已发布 Workflow 跑通发送、查询、成功展示。
4. 用一个 HITL Workflow 跑通 waiting/resume。
5. 再做自动路由，不把路由复杂度前置到 MVP。
