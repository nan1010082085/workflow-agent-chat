# 落地任务清单

本清单以 `PRD.md`、`ARCHITECTURE.md` 和 `UIUX.md` 为约束，目标是把当前骨架推进到可上线的 MVP。任务状态以 GitHub Issue/Project 为准；这里保留任务定义、依赖和验收口径。

## 状态与优先级

- `P0`：MVP 闭环或上线前置，必须完成。
- `P1`：提升可用性和可靠性，MVP 后紧接着完成。
- `P2`：增强能力，不阻塞首个版本。

## 0. 契约与设计前置

| ID | 类型 | 优先级 | 任务 | 依赖 | 完成标准 |
|---|---|---|---|---|---|
| C-01 | 产品/后端 | P0 | 确认 Agent Catalog contract | 无 | 字段、发布态、租户过滤、版本语义和错误码形成接口文档与示例 JSON |
| C-02 | 后端 | P0 | 确认 Runtime invoke/resume/cancel/status contract | 无 | 明确请求头、请求体、状态机、超时、幂等和错误映射，并有 contract test fixture |
| C-03 | 安全 | P0 | 确认 JWT、租户和 Runtime credential 链路 | C-01 | 明确用户身份、租户上下文、服务凭证边界，前端不持有 Workflow Key |
| D-01 | UI/UX | P0 | 输出桌面/平板/移动端高保真稿 | C-01 | 覆盖工作台、Agent 浏览、空/运行/等待/成功/失败/取消六类核心状态 |
| D-02 | UI/UX | P0 | 建立设计 token 与组件状态表 | D-01 | 颜色、字号、间距、圆角、阴影、断点，以及 hover/focus/disabled/loading/error/empty/long text 状态可交付 |
| D-03 | UI/UX | P0 | 用真实 Workflow 输出做内容验证 | C-02, D-01 | Markdown、表格、代码、结构化 JSON、下载链接和长文本在稿件中有明确展示方案 |
| D-04 | UI/UX | P0 | 完成可访问性和响应式评审 | D-01, D-02 | 键盘路径、焦点顺序、aria-label、对比度、窄屏无遮挡通过检查 |

## 1. MVP 后端

| ID | 类型 | 优先级 | 任务 | 依赖 | 完成标准 |
|---|---|---|---|---|---|
| B-01 | 后端 | P0 | 建立 Spring Boot 基础设施 | C-03 | 统一配置、环境变量、错误响应、请求日志、健康检查和 CORS 可用 |
| B-02 | 后端 | P0 | 实现租户上下文与权限校验 | C-03 | 所有 session/message/run/agent 查询带租户约束，跨租户访问返回统一 404/403 |
| B-03 | 后端 | P0 | 实现 session/message/run 领域模型 | 无 | Entity、Repository、Service 与 Flyway schema 对齐，支持快照字段和状态流转 |
| B-04 | 后端 | P0 | 实现 Agent Catalog adapter | C-01, B-02 | 只返回当前租户可见且已发布 Agent；Runtime 不可用时返回可识别错误 |
| B-05 | 后端 | P0 | 实现 Runtime REST adapter | C-02, C-03 | invoke/status/resume/cancel 均有超时、错误映射、重试边界和 correlation id |
| B-06 | 后端 | P0 | 实现发送消息闭环 | B-03, B-04, B-05 | 用户消息、assistant placeholder、run 映射原子落库；返回 `messageId/runId/status` |
| B-07 | 后端 | P0 | 实现轮询与状态同步 | B-05, B-06 | running/success/error/waiting/cancelled 映射正确，刷新后可恢复，避免重复 assistant message |
| B-08 | 后端 | P0 | 实现 HITL resume | B-07 | waiting 状态返回结构化审批/补充信息 schema，resume 后状态和消息正确更新 |
| B-09 | 后端 | P0 | 实现 cancel、幂等和故障处理 | B-06, B-07 | 重复提交不重复执行；超时、Runtime 5xx、断线和取消均可重试或明确失败 |
| B-10 | 后端 | P0 | 补齐后端自动化测试 | B-02-B-09 | 覆盖租户隔离、状态机、Runtime adapter、幂等、HITL 和错误响应 |

## 2. MVP 前端

| ID | 类型 | 优先级 | 任务 | 依赖 | 完成标准 |
|---|---|---|---|---|---|
| F-01 | 前端 | P0 | 建立 API client、类型和状态管理 | B-06 | API 类型与契约一致，统一 loading/error/retry 处理，禁止把 Runtime credential 放入浏览器 |
| F-02 | 前端 | P0 | 实现 AppShell 与响应式布局 | D-01, D-02 | 桌面三栏、平板两栏、移动单栏/抽屉按设计稿实现，无内容遮挡 |
| F-03 | 前端 | P0 | 实现 Session Navigator | B-03, D-01 | 新建、切换、列表、最近更新、空态、加载态和错误态可用 |
| F-04 | 前端 | P0 | 实现 Agent Catalog 与 AgentPicker | B-04, D-01 | 展示发布态 Agent、描述和能力；当前 Agent 始终可见，长名称可读 |
| F-05 | 前端 | P0 | 实现 Conversation Canvas 与 Composer | B-06, D-01 | 发送文本、Enter 发送、禁用态、失败重试和刷新恢复可用 |
| F-06 | 前端 | P0 | 实现消息与结果渲染 | B-07, D-03 | user/assistant/system 状态清晰，支持 Markdown、表格、代码块、结构化结果和复制 |
| F-07 | 前端 | P0 | 实现 RunStatusBar 与 ExecutionTimeline | B-07, D-01 | running/waiting/success/failed/cancelled 一眼可辨，详情默认折叠且可展开 |
| F-08 | 前端 | P0 | 实现 ApprovalCard | B-08, D-01 | 清楚说明原因、动作和字段；批准/拒绝/提交有防重复提交和反馈 |
| F-09 | 前端 | P0 | 实现 Agent 浏览与详情入口 | B-04, D-01 | Agent 卡片、能力标签、更新时间和开始对话可用 |
| F-10 | 前端 | P0 | 补齐前端组件测试与 E2E 主流程 | F-01-F-09 | 覆盖选择 Agent、发送、成功、失败、waiting/resume、刷新恢复和移动端布局 |

## 3. 设计系统与设计落地

| ID | 类型 | 优先级 | 任务 | 完成标准 |
|---|---|---|---|---|
| DS-01 | 设计 | P0 | 建立颜色、排版、间距、圆角和图标规范 | 与 UIUX 视觉方向一致；错误色只表达错误；状态不依赖颜色单独传达 |
| DS-02 | 设计 | P0 | 交付核心组件规格 | AppShell、Sidebar、AgentCard、MessageBubble、Composer、StatusBar、Timeline、ApprovalCard、ResultRenderer 有尺寸与状态标注 |
| DS-03 | 设计 | P0 | 交付内容与状态文案 | 空态、加载、失败、等待、取消、重试、权限不足和 Runtime 不可用文案明确可执行 |
| DS-04 | 设计/前端 | P0 | 建立设计稿到代码的验收清单 | 逐页核对间距、字号、交互状态、键盘操作、断点和真实数据，不以“看起来像”为验收 |
| DS-05 | 设计 | P1 | 设计文件附件、下载和复杂结果 | 明确文件预览、下载、表格溢出、代码复制和结构化 JSON 的交互边界 |

## 4. 生产化与交付

| ID | 类型 | 优先级 | 任务 | 依赖 | 完成标准 |
|---|---|---|---|---|---|
| O-01 | DevOps | P0 | 完善本地开发与 CI | B-01, F-01 | 提供 backend/frontend 启动说明、环境变量模板、构建、测试和 lint 检查 |
| O-02 | 安全 | P0 | 完成权限、限流和敏感信息检查 | B-02, B-05 | 凭证不进日志/前端；按租户和用户限流；越权、注入和错误信息泄露有测试 |
| O-03 | 观测 | P0 | 增加日志和核心指标 | B-05, B-07 | 可按 tenant/session/run 追踪；有首响应、完成耗时、成功率和 Runtime 错误率 |
| O-04 | 发布 | P0 | 集成测试、灰度和回滚方案 | B-10, F-10 | 一个普通 Workflow 和一个 HITL Workflow 在测试环境跑通；发布、回滚、备份和数据保留已记录 |
| O-05 | 体验 | P1 | SSE 替换轮询，保持前端 API 不变 | B-07 | 断线重连、事件顺序、重复事件和完成事件处理稳定 |
| O-06 | 体验 | P1 | 文件附件、搜索、收藏和最近使用 | DS-05 | P1 体验闭环可用，不影响文本消息主流程 |
| O-07 | 产品 | P2 | 自动 Agent 路由与多 Agent 能力 | C-01, O-04 | Catalog 完整、权限可控，且有可解释的路由与失败回退方案 |

## 推荐执行顺序

1. `C-01`-`C-03` 与 `D-01`-`D-04` 并行，先冻结接口和核心状态设计。
2. 完成 `B-01`-`B-06` 与 `F-01`-`F-05`，跑通“选 Agent -> 发消息 -> 返回 runId”。
3. 完成 `B-07`-`B-10` 与 `F-06`-`F-10`，跑通成功、失败、刷新恢复和 HITL。
4. 完成 `DS-01`-`DS-04`、`O-01`-`O-04`，形成可验收的 MVP 发布包。
5. 再做 `O-05`-`O-07`，不要把自动路由和复杂编排前置到 MVP。

## MVP 完成定义

- 一个已发布普通 Workflow 能在目标租户中被发现、执行并展示最终结果。
- 一个 HITL Workflow 能进入 waiting，用户能批准/拒绝/补充信息并继续执行。
- 刷新、重复提交、Runtime 不可用和跨租户访问都有明确且可测试的行为。
- 桌面与移动端通过 UI/UX 验收；核心路径通过自动化测试。
- GitHub Issue、PR 和发布记录能回溯到本清单中的任务 ID。
