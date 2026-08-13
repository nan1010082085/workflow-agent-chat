# UI/UX 设计说明

## 1. 设计目标

让用户在最少认知负担下完成一个业务任务，并始终理解：当前 Agent、当前状态、下一步动作、最终结果。

## 2. 页面结构

### 工作台

工作台是默认首页，采用三栏结构：

- 左栏 260-300px：Session Navigator。负责会话切换，不承载 Agent 配置。
- 中栏：Conversation Canvas。负责消息、输入和结果。
- 右栏 280-340px：Agent & Run Inspector。负责当前 Agent 信息和执行过程。

右栏可收起；移动端变成底部抽屉或顶部详情页。

### Agent 浏览

Agent 浏览页用于发现能力，不承担执行。每个 Agent 项包含：

- 图标与名称
- 一句话描述
- 能力标签
- 支持文本/文件/审批等标识
- 最近更新时间
- 开始对话按钮

## 3. 组件清单

```text
AppShell
SessionSidebar
AgentPicker
AgentCard
ConversationHeader
MessageList
MessageBubble
RunStatusBar
ExecutionTimeline
ApprovalCard
ResultRenderer
Composer
AttachmentButton (P1)
```

## 4. 交互细节

### 发送

1. 用户发送后立即落一条 user message。
2. 创建 assistant placeholder，状态为 `running`。
3. 右栏展示 Runtime run 状态和已完成节点。
4. 完成后填充结果；失败时保留失败上下文，不伪装成普通回复。

### 审批

审批卡片必须说明：

- 为什么需要用户操作
- 将要执行什么
- 可选答案/字段
- 批准、拒绝、提交按钮

危险动作使用明确的破坏性操作色；普通补充信息使用主色。

### 结果

结果内容支持 Markdown、表格、代码块、结构化 JSON 和下载链接。执行详情默认折叠，用户主动展开。

## 5. 可访问性与响应式

- 所有图标按钮提供 tooltip 和 aria-label。
- 键盘可完成 Agent 切换、发送和审批。
- 颜色不作为唯一状态信息，状态同时有文字和图标。
- 断点：`>= 1200px` 三栏，`768-1199px` 两栏，`< 768px` 单栏。
- 文本不依赖视口缩放，长 Agent 名称和按钮允许换行。

## 6. 设计验收

- 首屏无需滚动即可看到当前 Agent、对话空状态和输入框。
- running/waiting/success/failed/cancelled 可一眼区分。
- 任意消息都能追溯到对应 run。
- 刷新和窄屏下布局不发生内容遮挡。
