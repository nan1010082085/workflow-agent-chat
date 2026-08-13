# Workflow Agent Chat PRD

## 1. 产品定义

Workflow Agent Chat 是一个独立的通用对话产品。用户通过自然语言与已发布的 Workflow Agent 交互，Workflow 的创建、配置、发布、调试和监控继续由 Schema Platform AI 管理。

核心原则：`Published Workflow = Chat Agent`。内置 Workflow 与用户新创建并发布的 Workflow，在 Chat 中统一视为 Agent。

### 产品定位

产品名称暂定为 **Workflow Agent Chat**，定位是“面向业务任务的 Workflow Agent 消费端”。它让用户选择一个 Agent，用对话完成任务；用户不需要理解 Workflow、节点、Prompt 或 LangGraph。

产品价值不是提供一个空泛的 AI 问答框，而是让用户获得三种确定性：

1. 知道当前使用的是哪个 Agent。
2. 知道任务目前执行到哪一步、是否需要自己介入。
3. 能拿到可复核、可继续、可追踪的结果。

### 产品关系

```text
AI Platform / ai/app       = Agent Builder + Schema Platform Copilot
Workflow Agent Chat        = Agent Consumer + Task Workspace
AI Runtime                 = Workflow execution infrastructure
```

`ai/app` 中面向 Editor/Flow 的 Copilot 保持不变；本产品不承接 schema、flow、node 上下文，也不复用其 Chat 页面。

## 2. 非目标

- 不替代 `ai/app` 的 Schema Platform Copilot。
- 不提供 Schema/Flow 编辑器上下文操作。
- 不在 Chat 项目中编辑或发布 Workflow。
- 不把 LangGraph 图复制到 Chat 项目。
- 不让 Chat 直接访问 AI Runtime 数据库。

## 3. 目标用户与场景

| 用户 | 场景 | 价值 |
|---|---|---|
| 业务用户 | 选择一个 Agent 完成审核、提取、问答、生成等任务 | 用对话替代复杂表单操作 |
| 租户管理员 | 控制租户可用 Agent | 安全复用平台能力 |
| 平台管理员 | 在 AI Platform 创建并发布 Workflow | 一次建设，多端消费 |

## 4. MVP 范围

### P0

- 登录态接入（第一阶段支持 Chat Backend 代理现有 JWT，独立账号体系后置）
- Agent Catalog：仅展示当前租户可见、已发布的 Workflow
- 新建会话、会话列表、消息历史
- 用户选择 Agent 并发送文本消息
- 调用 Runtime Open API，保存 Chat Message 与 Runtime Run 映射
- 展示运行中、成功、失败状态和最终结果
- 轮询适配器；统一设计为可替换 SSE
- waiting 状态展示审批卡片，支持 resume
- 取消运行

### P1

- 自动选择 Agent
- 文件附件
- SSE 实时事件流
- Agent 搜索、收藏、最近使用
- 用量、耗时、错误等基础信息

### P2

- 多 Agent 协作展示
- Agent 组合与临时任务链
- 组织级 Agent 权限策略
- 对话分享和外部嵌入

## 5. 核心流程

```text
打开 Chat
  -> 获取 Agent Catalog
  -> 选择 Agent / 后续自动路由
  -> 创建或复用 Session
  -> POST message
  -> Backend 调 Runtime invoke
  -> 返回 runId
  -> 订阅/轮询运行状态
  -> 保存并展示 assistant message
  -> waiting 时展示 HITL
  -> resume 后继续运行
```

## 6. MVP 验收标准

1. 已发布 Workflow 能在 Agent 列表中出现，草稿不可见。
2. 发送一条消息后，用户能看到运行状态和最终输出；刷新页面不会丢失状态。
3. Runtime 返回 `waiting` 时，用户能提交批准/拒绝/答案并继续执行。
4. Chat 会话数据只保存在本项目 MySQL；Workflow 执行数据仍由 Runtime 管理。
5. 用户不能通过 Chat 读取其他租户的 Agent、Session 或 Run。
6. Runtime 不可用时，界面明确显示失败，不产生幽灵消息。

## 7. 关键产品决策

- 第一版以“用户选择 Agent”为主，自动路由必须建立在 Agent Catalog 完整之后。
- Chat 的 session/message 是产品数据；Workflow execution 是基础设施数据，两者通过 `runtime_execution_id` 关联。
- 对外只暴露 Chat Backend API；前端不持有 Workflow Key。

## 8. 信息架构

MVP 不是营销首页，而是直接进入工作台：

```text
Workflow Agent Chat
├── 工作台 / Chat
│   ├── 新建会话
│   ├── 最近会话
│   ├── Agent 选择器
│   ├── 当前对话
│   └── 执行详情
├── Agent 浏览
│   ├── 全部 Agent
│   ├── 最近使用
│   └── Agent 详情
└── 设置
    ├── 个人设置
    └── 使用记录（P1）
```

## 9. UI/UX 设计原则

- **任务优先**：打开即能开始任务，不放大面积营销 Hero。
- **选择明确**：当前 Agent 始终可见，避免用户不知道回答来自哪里。
- **过程透明但不过载**：默认展示执行阶段和节点状态，不默认展示完整思考链。
- **结果可操作**：支持复制、下载、重试、继续对话和查看执行详情。
- **人工介入清晰**：`waiting` 必须明显区别于失败；审批动作集中在一张卡片内。
- **密度适中**：桌面三栏，移动端抽屉化；不使用卡片套卡片。

## 10. 主界面线框

### Desktop 工作台

```text
┌──────────────┬─────────────────────────────────┬────────────────┐
│ 会话导航      │ 当前对话                          │ Agent / Run     │
│              │                                 │                │
│ + 新建会话    │ [Agent 名称]   Published         │ Agent 描述      │
│ 搜索会话      │                                 │ 输入摘要        │
│              │ 用户消息                         │                │
│ 今天          │ Agent 最终结果                   │ 执行状态        │
│ · 审核报销    │                                 │ ✓ 解析材料      │
│ · 合同检查    │                                 │ ✓ 查询规则      │
│              │ ┌─────────────────────────────┐ │ ◌ 生成结论      │
│ 更早          │ │ 描述你想完成的任务...       │ │                │
│              │ └─────────────────────────────┘ │ [查看详情]      │
└──────────────┴─────────────────────────────────┴────────────────┘
```

### Mobile

```text
┌──────────────────────────┐
│ Agent 名称       ☰ 详情  │
├──────────────────────────┤
│ 对话消息                  │
│ 执行状态折叠条            │
│                          │
│ 输入框              发送  │
└──────────────────────────┘
```

## 11. 关键交互状态

| 状态 | UI 表现 | 用户动作 |
|---|---|---|
| idle | 空状态 + Agent 说明 + 示例任务 | 开始输入 |
| running | assistant 占位消息 + 执行时间线 | 停止 |
| waiting | 高优先级审批/补充信息卡 | 批准、拒绝、填写 |
| success | 最终结果 + 过程摘要 | 复制、下载、继续 |
| failed | 失败原因 + 可重试按钮 | 重试或更换 Agent |
| cancelled | 已取消状态 | 重新运行 |

## 12. 视觉方向

- 基调：安静、可信、工作型，不做聊天娱乐化。
- 主色：深青绿色用于执行和主操作；暖橙色作为 Agent/任务强调色；红色只用于错误。
- 背景：浅冷灰；内容区保持白色和清晰分隔线。
- 圆角：4-8px；按钮优先使用图标和明确 tooltip。
- 字体：系统无衬线字体；标题克制，避免巨型 Hero 字号。
- Agent 卡片显示图标、名称、短描述、支持的输入类型、最近更新时间。
