# Workflow Agent Chat 前端文档

> 技术栈：Vue 3 + TypeScript + Vite + Pinia + Vue Router
>
> 本文档涵盖前端架构、组件设计、状态管理、交互流程和开发指南。

---

## 目录

- [1. 技术架构](#1-技术架构)
- [2. 目录结构](#2-目录结构)
- [3. 路由设计](#3-路由设计)
- [4. 状态管理 (Pinia Stores)](#4-状态管理-pinia-stores)
- [5. 组件体系](#5-组件体系)
- [6. API 层设计](#6-api-层设计)
- [7. 类型系统](#7-类型系统)
- [8. 核心交互流程](#8-核心交互流程)
- [9. UI/UX 设计规范](#9-uiux-设计规范)
- [10. 工具函数](#10-工具函数)
- [11. 开发指南](#11-开发指南)

---

## 1. 技术架构

### 1.1 技术栈

| 层 | 技术 | 版本 |
|---|---|---|
| 框架 | Vue 3 (Composition API) | 3.5+ |
| 类型 | TypeScript | 5.8+ |
| 构建 | Vite | 6.4+ |
| 状态 | Pinia | 2.x |
| 路由 | Vue Router | 4.x |
| 样式 | Scoped CSS (无 UI 库) | - |
| HTTP | Fetch API (自封装) | - |
| WS | Socket.IO (平台对接) | - |

### 1.2 架构分层

```
┌─────────────────────────────────────────────────────────────┐
│                      Views (页面)                           │
│  LoginView │ WorkspaceView │ AgentBrowseView               │
├─────────────────────────────────────────────────────────────┤
│                    Components (组件)                         │
│  MessageBubble │ Composer │ ProcessingDrawer │ ...         │
├─────────────────────────────────────────────────────────────┤
│                   Stores (状态管理)                          │
│  auth │ session │ chat │ agent │ model                      │
├─────────────────────────────────────────────────────────────┤
│                     API (数据层)                             │
│  client.ts │ platformSocket.ts                              │
├─────────────────────────────────────────────────────────────┤
│                   Utils (工具函数)                           │
│  messageContent.ts │ textParser.ts                          │
└─────────────────────────────────────────────────────────────┘
```

### 1.3 数据流向

```
用户操作 → View → Store action → API 请求 → 后端接口
    ↑                                            │
    └──────────── Store state ←── Response ──────┘
         │
         └──→ View 响应式更新
```

---

## 2. 目录结构

```
frontend/src/
├── api/                          # API 层
│   ├── client.ts                 # HTTP 客户端封装
│   └── platformSocket.ts         # 平台 WebSocket 对接
│
├── components/                   # 组件
│   ├── message/                  # 消息相关子组件
│   │   ├── MessageParts.vue      # 消息内容解析渲染
│   │   ├── MessageAttachmentList.vue  # 附件列表
│   │   └── DocumentSummaryList.vue    # 文档摘要
│   ├── AppMark.vue               # 应用 Logo
│   ├── ApprovalCard.vue          # HITL 审批卡片
│   ├── AssistantPicker.vue       # 助手选择器
│   ├── Composer.vue              # 消息输入框
│   ├── ConversationHeader.vue    # 会话头部
│   ├── MessageBubble.vue         # 消息气泡
│   ├── MessageList.vue           # 消息列表
│   ├── ModelPicker.vue           # 模型选择器
│   ├── ProcessingDrawer.vue      # 处理信息抽屉
│   ├── RunStatusBar.vue          # 运行状态条
│   ├── SessionSidebar.vue        # 会话侧栏
│   └── UserMenu.vue              # 用户菜单
│
├── router/                       # 路由
│   └── index.ts                  # 路由配置与守卫
│
├── stores/                       # Pinia 状态管理
│   ├── auth.ts                   # 认证状态
│   ├── session.ts                # 会话状态
│   ├── chat.ts                   # 聊天状态（核心）
│   ├── agent.ts                  # 助手目录
│   └── model.ts                  # 模型列表
│
├── types/                        # 类型定义
│   └── index.ts                  # 与后端 API 契约对齐
│
├── utils/                        # 工具函数
│   ├── messageContent.ts         # 消息内容解析
│   └── textParser.ts             # 文本解析
│
├── views/                        # 页面视图
│   ├── LoginView.vue             # 登录页
│   ├── WorkspaceView.vue         # 工作台（主页面）
│   └── AgentBrowseView.vue       # 助手浏览页
│
├── App.vue                       # 根组件
├── main.ts                       # 入口文件
└── style.css                     # 全局样式
```

---

## 3. 路由设计

### 3.1 路由表

| 路径 | 名称 | 组件 | 权限 | 说明 |
|------|------|------|------|------|
| `/login` | login | LoginView | 公开 | 登录/注册页 |
| `/` | - | - | 需认证 | 重定向到 `/chat` |
| `/chat` | workspace | WorkspaceView | 需认证 | 工作台（无会话） |
| `/chat/:sessionId` | session | WorkspaceView | 需认证 | 工作台（指定会话） |

### 3.2 路由守卫

```typescript
router.beforeEach(async (to) => {
  const auth = useAuthStore()

  // 1. 首次加载时恢复认证状态
  if (!auth.bootstrapped) {
    await auth.bootstrap()
  }

  // 2. 公开页面处理
  if (to.meta.public) {
    // 已登录用户访问 /login → 重定向到 /chat
    if (auth.isAuthenticated && to.name === 'login') {
      return { path: '/chat' }
    }
    return true
  }

  // 3. 受保护页面：未登录 → 跳转登录页
  if (!auth.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  return true
})
```

### 3.3 启动流程

```
页面加载
  ↓
App.vue 挂载
  ↓
showBoot = true (显示加载页)
  ↓
auth.bootstrap() 执行
  ├── 有 accessToken → 调用 /auth/me 验证
  │   ├── 成功 → 更新用户信息
  │   └── 失败 → 尝试 refreshToken
  │       ├── 成功 → 更新 token
  │       └── 失败 → clearSession()
  └── 无 accessToken → 直接完成
  ↓
bootstrapped = true
  ↓
showBoot = false (隐藏加载页)
  ↓
路由守卫判断
  ├── 已登录 → 显示 Chat 壳
  └── 未登录 → 跳转 /login
```

---

## 4. 状态管理 (Pinia Stores)

### 4.1 Auth Store (`auth.ts`)

**职责：** 管理用户认证状态、Token 持久化、登录/注册/登出。

```typescript
interface AuthUserInfo {
  id: string
  username: string
  displayName?: string
  tenantId?: string
  roles?: string[]
}

// State
const accessToken = ref<string | null>()      // JWT access token
const refreshToken = ref<string | null>()     // JWT refresh token
const user = ref<AuthUserInfo | null>()       // 当前用户信息
const bootstrapped = ref(false)               // 是否已完成初始化
const loading = ref(false)
const error = ref<string | null>()

// Computed
const isAuthenticated = computed(() => Boolean(accessToken.value && user.value))

// Actions
async function bootstrap()                    // 启动时恢复会话
async function login(username, password, tenantCode?)  // 登录
async function register(payload)              // 注册（自动登录）
async function logout()                       // 登出
async function tryRefresh()                   // 刷新 Token
function clearSession()                       // 清除本地会话
```

**持久化策略：**
- `wac_access_token` → localStorage
- `wac_refresh_token` → localStorage
- `wac_user` → localStorage (JSON)

---

### 4.2 Session Store (`session.ts`)

**职责：** 管理会话列表、当前会话、会话 CRUD。

```typescript
// State
const sessions = ref<SessionSummary[]>([])    // 会话列表
const currentSessionId = ref<string | null>() // 当前选中会话 ID
const loading = ref(false)
const error = ref<string | null>()

// Computed
const current = computed(() => sessions.value.find(s => s.id === currentSessionId.value))

// Actions
async function fetchSessions()                // 获取会话列表
async function createSession(agentId?, agentName?, title?)  // 创建会话
async function updateTitle(sessionId, title)  // 更新标题
function select(id)                           // 选择会话
function bumpSession(sessionId, patch)        // 更新会话（置顶）
```

**辅助函数：**
```typescript
function titleFromContent(content: string): string
// 从首条用户消息生成侧栏标题（截取前 40 字符）
```

---

### 4.3 Chat Store (`chat.ts`) ⭐ 核心

**职责：** 管理当前会话消息、运行状态、轮询、HITL 交互。

```typescript
// State
const messages = ref<Message[]>([])           // 当前会话消息列表
const currentRun = ref<RunStatusView | null>() // 当前运行状态
const sending = ref(false)                    // 是否正在发送
const error = ref<string | null>()
const loadingMessages = ref(false)
const modelMessages = ref<Message[]>([])      // 模型对话消息
const runIdByExec = ref<Record<string, string>>({})  // executionId → runId 映射
const loadedSessionId = ref<string | null>()  // 已加载的会话 ID

// Actions
async function fetchMessages(sessionId)       // 获取消息列表
async function sendMessage(sessionId, agentId, content, attachmentIds?)
// 发送消息（助手模式）→ 触发 Runtime 执行

async function sendModelMessage(sessionId, modelId, content, attachmentIds?)
// 发送消息（模型模式）→ 平台 WS 流式 或 同步补全

async function fetchRun(runId)                // 查询运行状态
async function resumeRun(runId, action, payload?)  // HITL 恢复
async function resumeWaiting(action, payload?)      // 审批提交（自动查找 runId）
async function cancelRun(runId)               // 取消运行
async function resumeFromSession(sessionId)   // 切换会话时恢复状态
function stopPolling()                        // 停止轮询
function reset()                              // 重置状态
```

**轮询机制：**

```
发送消息 → 获取 runId
    ↓
startPolling(runId)
    ↓
┌──→ fetchRun(runId) ←──────────────────┐
    ↓                                    │
检查 status                              │
├── RUNNING → setTimeout 2s ─────────────┘
├── WAITING_INPUT → setTimeout 4s ───────┘
└── 终态 (COMPLETED/FAILED/CANCELLED) → stopPolling()
    ↓
回拉消息 fetchMessages()
```

**消息发送流程（助手模式）：**

```
1. 插入临时 user + assistant 消息（optimistic UI）
2. 调用 POST /sessions/{id}/messages
3. 更新临时消息 ID 为真实 ID
4. 保存 runIdByExec 映射
5. 根据返回状态：
   ├── WAITING_INPUT → 立即 fetchRun() 获取审批载荷
   ├── RUNNING → startPolling()
   └── 终态 → fetchRun()
6. 回拉消息列表获取完整附件元数据
```

**消息发送流程（模型模式）：**

```
1. 插入临时 user + assistant 消息
2. 尝试平台 WS 流式：
   ├── 成功 → 实时更新 assistant.content/thinking
   └── 失败 → 回退同步补全 POST /sessions/{id}/completions
3. 流式完成后调用 POST /sessions/{id}/model-turns 落库
4. 回拉消息列表
```

---

### 4.4 Agent Store (`agent.ts`)

**职责：** 管理助手目录。

```typescript
// State
const agents = ref<Agent[]>([])
const loading = ref(false)
const error = ref<string | null>()

// Actions
async function fetchAgents()                  // 获取助手列表
function getAgent(id): Agent | undefined      // 按 ID 或 slug 查找助手
```

**输入能力兜底 (`enrichSupportedInputs`)：**

```typescript
function enrichSupportedInputs(slug: string, inputs: string[]): string[]
// 根据 slug 关键词自动添加 file/image 支持
// 关键词: document, contract, resume, expense, image, pdf, ocr, vision
```

---

### 4.5 Model Store (`model.ts`)

**职责：** 管理模型列表和选中模型。

```typescript
// State
const models = ref<ChatModel[]>([])
const selectedModelId = ref<string | null>()
const loading = ref(false)
const error = ref<string | null>()

// Actions
async function fetchModels()                  // 获取模型列表
function selectModel(id)                      // 选择模型
function selected(): ChatModel | null         // 获取当前选中模型
```

---

## 5. 组件体系

### 5.1 组件层级

```
App.vue
├── [未认证] RouterView → LoginView
└── [已认证] app-shell
    ├── SessionSidebar (侧栏)
    │   ├── AppMark (Logo)
    │   ├── 新建会话按钮
    │   ├── 会话列表
    │   └── UserMenu
    └── main (主内容区)
        └── WorkspaceView
            ├── ConversationHeader
            │   ├── 助手名称 + 状态
            │   └── ModelPicker / AssistantPicker
            ├── MessageList
            │   └── MessageBubble (循环)
            │       ├── AppMark (助手头像)
            │       ├── MessageParts (消息内容)
            │       │   ├── Markdown 渲染
            │       │   ├── 代码块
            │       │   └── 文档摘要
            │       ├── MessageAttachmentList (附件)
            │       ├── ApprovalCard (HITL 审批)
            │       ├── thinking 折叠区
            │       └── toolCalls 折叠区
            ├── ProcessingDrawer (处理信息抽屉)
            │   └── RunStatusBar
            └── Composer (输入框)
                ├── 流光动画 SVG
                ├── pending 附件列表
                ├── textarea
                └── 发送按钮
```

### 5.2 核心组件详解

#### 5.2.1 Composer.vue (消息输入框)

**功能：**
- 多行文本输入，自动高度
- 文件附件上传（拖拽 + 粘贴 + 选择）
- 流光边框动画（双向流动）
- WebSocket 状态指示器
- 快捷键发送 (Ctrl/Cmd + Enter)

**Props：**
```typescript
interface Props {
  disabled?: boolean           // 是否禁用
  placeholder?: string         // 占位符
}
```

**Events：**
```typescript
emit('send', content: string, attachmentIds: string[])
```

**附件上传流程：**
```
选择文件 → 显示 pending 附件 → 上传到 /api/chat/uploads
    ↓
上传成功 → 替换为真实 ID
上传失败 → 显示错误状态
    ↓
发送消息时携带 attachmentIds
```

---

#### 5.2.2 MessageBubble.vue (消息气泡)

**功能：**
- 区分 user/assistant 角色样式
- Markdown 内容渲染
- thinking 过程折叠（流式时自动展开，结束后收起）
- toolCalls 折叠显示
- 文档摘要展示
- 附件列表（图片内联预览、PDF 预览、其他下载）
- HITL 审批卡片
- 复制、重试等操作

**Props：**
```typescript
interface Props {
  message: Message
  run?: RunStatusView | null
}
```

**状态映射：**
```typescript
// Message.status → UI 表现
PENDING     → 排队中
RUNNING     → 处理中 + 动画
WAITING_INPUT → 需要确认 + 审批卡片
COMPLETED   → 完成
FAILED      → 失败 + 重试按钮
CANCELLED   → 已取消
```

---

#### 5.2.3 ProcessingDrawer.vue (处理信息抽屉)

**功能：**
- 显示当前运行的详细状态
- 实时耗时计时器
- 运行节点时间线
- 取消运行按钮

**Props：**
```typescript
interface Props {
  open: boolean                // 是否展开
  run: RunStatusView | null    // 运行状态
  agent: Agent | null          // 当前助手
  message?: Message | null     // 当前消息
  modelName?: string | null    // 模型名称
  mode?: 'model' | 'agent'    // 模式
}
```

**Events：**
```typescript
emit('close')
emit('cancel')
```

---

#### 5.2.4 ApprovalCard.vue (HITL 审批卡片)

**功能：**
- 展示审批提示
- 动态表单字段（text, select, confirm）
- 操作按钮（approve, reject, 自定义）
- 危险操作警告

**Props：**
```typescript
interface Props {
  waiting: WaitingPayload      // 审批载荷
  sending?: boolean            // 是否提交中
}
```

**Events：**
```typescript
emit('action', action: string, payload?: string)
```

---

#### 5.2.5 SessionSidebar.vue (会话侧栏)

**功能：**
- 会话列表展示
- 新建会话
- 会话搜索
- 会话标题编辑
- 当前会话高亮
- 响应式折叠（移动端）

---

#### 5.2.6 AssistantPicker.vue (助手选择器)

**功能：**
- 助手列表展示
- 搜索过滤
- 助手详情预览
- 支持的输入类型显示
- HITL 能力标识

---

#### 5.2.7 ModelPicker.vue (模型选择器)

**功能：**
- 模型列表展示
- 选中模型高亮
- 切换模型

---

### 5.3 消息子组件

#### MessageParts.vue

**功能：** 解析消息内容并渲染为结构化组件。

- Markdown 渲染（标题、列表、表格、代码块）
- 代码高亮
- 链接识别
- 文档摘要展示

#### MessageAttachmentList.vue

**功能：** 展示消息附件列表。

- 图片内联预览（点击放大）
- PDF iframe 预览
- 文件类型标签（Word, Excel, CSV, JSON）
- 文件大小显示
- 下载链接

#### DocumentSummaryList.vue

**功能：** 展示文档处理摘要。

- 文档名称
- 摘要内容
- 页数信息

---

## 6. API 层设计

### 6.1 HTTP Client (`client.ts`)

**核心特性：**
- 自动注入 JWT Token
- 401 时自动尝试 refresh
- 统一错误处理
- 用户友好的错误消息

```typescript
class ApiError extends Error {
  code: string           // 错误码
  message: string        // 用户友好消息
  details?: string[]     // 详细信息
}

// 错误码映射
UNAUTHORIZED → '请先登录'
CATALOG_UNAVAILABLE → '智能体暂时不可用，请稍后重试'
MODEL_UNAVAILABLE → '当前模型暂时不可用，请换一个模型'
RATE_LIMITED → '请求过于频繁，请稍后再试'
NETWORK_ERROR → '网络连接异常，请稍后重试'
```

**API 方法：**

```typescript
export const api = {
  // 认证
  login(data),
  register(data),
  refresh(refreshToken),
  me(),
  logout(),

  // 模型
  listModels(),
  complete(data),

  // 助手
  listAgents(),

  // 会话
  listSessions(),
  createSession(data),
  updateTitle(sessionId, title),

  // 消息
  listMessages(sessionId),
  sendMessage(sessionId, data),
  completeInSession(sessionId, data),
  persistModelTurn(sessionId, data),

  // 上传
  uploadFile(file, sessionId?),

  // 运行
  getRun(runId),
  getRunByExecution(executionId),
  resumeRun(runId, data),
  cancelRun(runId),
}
```

---

### 6.2 平台 WebSocket (`platformSocket.ts`)

**功能：** 对接平台 Socket.IO，实现模型对话流式。

```typescript
async function streamModelChatViaPlatform(options: {
  message: string
  conversationId?: string | null
  llmModel?: string
  agents: Agent[]
  priorMessages: Message[]
  onEvent: (event: { type: string; content?: string }) => void
}): Promise<{
  content: string
  thinking?: string
  conversationId?: string
  error?: string
}>
```

**事件类型：**
- `text_delta` - 文本增量
- `thinking_delta` - 思考过程增量
- `error` - 错误

---

## 7. 类型系统

### 7.1 核心类型 (`types/index.ts`)

```typescript
// 助手
interface Agent {
  id: string
  slug: string
  name: string
  description: string
  icon: string
  supportedInputs: string[]    // ['text', 'file', 'image']
  hitlCapable: boolean
  version: string
  updatedAt: string
  published: boolean
}

// 模型
interface ChatModel {
  id: string
  name: string
  model: string
  provider: string
  capabilities: string[]
  isDefault: boolean
}

// 会话
interface SessionSummary {
  id: string
  title: string
  agentId: string | null
  agentName: string | null
  platformConversationId?: string | null
  status: string               // 'ACTIVE' | 'ARCHIVED'
  createdAt: string
  updatedAt: string
}

// 消息
interface Message {
  id: string
  role: 'user' | 'assistant' | 'system'
  content: string
  thinking?: string            // 思考过程
  runtimeExecutionId: string | null
  status: MessageStatus
  createdAt: string
  tip?: string
  toolCalls?: ToolCallInfo[]
  documentSummaries?: MessageDocumentSummary[]
  workflowExecution?: WorkflowMessageExecution
  attachments?: MessageAttachment[]
}

// 消息状态
type MessageStatus = 'PENDING' | 'RUNNING' | 'WAITING_INPUT' | 'COMPLETED' | 'FAILED' | 'CANCELLED'

// 运行状态
interface RunStatusView {
  runId: string
  sessionId: string
  agentId: string
  runtimeExecutionId: string | null
  status: RunStatus
  errorMessage: string | null
  waiting: WaitingPayload | null
  startedAt: string
  finishedAt: string | null
}

// HITL 审批载荷
interface WaitingPayload {
  prompt: string               // 审批提示
  fields: WaitingField[]       // 表单字段
  actions: WaitingAction[]     // 操作按钮
  dangerous: boolean           // 是否危险操作
}

// 附件
interface MessageAttachment {
  id: string
  filename: string
  mimetype: string
  size?: number
  excerpt?: string | null
  url?: string
  createdAt?: string
}

// 待发送附件（含本地预览）
interface PendingAttachment {
  id: string
  filename: string
  mimetype: string
  size: number
  status: 'uploading' | 'done' | 'error'
  error?: string
  previewUrl?: string
}
```

---

## 8. 核心交互流程

### 8.1 登录流程

```
┌─────────────────────────────────────────────────────────┐
│                    LoginView                             │
├─────────────────────────────────────────────────────────┤
│  step=1: 用户名输入                                      │
│    ├── 输入用户名 (≥2字符)                               │
│    └── 点击「继续」→ step=2                              │
│                                                         │
│  step=2: 密码输入                                        │
│    ├── 输入密码                                          │
│    ├── 注册模式：密码强度验证 (≥8位,大小写+数字)          │
│    └── 点击「登录」/「注册」                             │
│                                                         │
│  成功 → 存储 Token → 跳转 /chat                         │
│  失败 → 显示错误信息                                     │
└─────────────────────────────────────────────────────────┘
```

### 8.2 发送消息流程（助手模式）

```
用户在 Composer 输入内容
    ↓
点击发送 / Ctrl+Enter
    ↓
chat.sendMessage(sessionId, agentId, content, attachmentIds)
    ↓
┌─ Optimistic UI ─────────────────────────┐
│ 插入临时 user 消息 (id: temp-xxx)       │
│ 插入临时 assistant 消息 (status: RUNNING)│
└─────────────────────────────────────────┘
    ↓
POST /api/chat/sessions/{id}/messages
    ↓
更新临时消息为真实 ID
    ↓
根据返回状态处理：
├── status = RUNNING → startPolling(runId)
├── status = WAITING_INPUT → fetchRun() 获取审批载荷
└── status = 终态 → fetchRun() + fetchMessages()
    ↓
轮询过程中：
├── RUNNING → 每 2s 查询一次
├── WAITING_INPUT → 每 4s 查询一次
└── 终态 → 停止轮询，回拉消息
```

### 8.3 HITL 审批流程

```
运行状态变为 WAITING_INPUT
    ↓
MessageBubble 检测到 status === 'WAITING_INPUT'
    ↓
显示 ApprovalCard
    ├── prompt: 审批提示文本
    ├── fields: 动态表单字段
    │   ├── type: 'text' → 输入框
    │   ├── type: 'select' → 下拉选择
    │   └── type: 'confirm' → 确认框
    └── actions: 操作按钮
        ├── approve (绿色) → 批准
        ├── reject (红色) → 拒绝
        └── 自定义操作
    ↓
用户填写表单并点击操作按钮
    ↓
chat.resumeWaiting(action, payload)
    ↓
┌─ 自动查找 runId ─────────────────────────┐
│ 1. 优先使用 currentRun.runId             │
│ 2. 否则按 runtimeExecutionId 反查        │
└─────────────────────────────────────────┘
    ↓
POST /api/chat/runs/{runId}/resume
    ↓
根据返回状态：
├── 继续运行 → startPolling()
├── 完成 → fetchMessages() 展示结果
└── 拒绝 → fetchMessages() + 自动发送修正内容
```

### 8.4 模型对话流程

```
用户在 Composer 输入内容（未选择助手）
    ↓
chat.sendModelMessage(sessionId, modelId, content)
    ↓
┌─ Optimistic UI ─────────────────────────┐
│ 插入临时 user + assistant 消息          │
└─────────────────────────────────────────┘
    ↓
尝试平台 WebSocket 流式：
├── 成功 ─────────────────────────────────┐
│   实时更新 assistant.thinking           │
│   实时更新 assistant.content            │
│   流式完成 → persistModelTurn() 落库    │
└─────────────────────────────────────────┘
├── 失败 → 回退同步补全 ─────────────────┐
│   POST /sessions/{id}/completions       │
│   直接获取完整响应                       │
└─────────────────────────────────────────┘
    ↓
回拉消息列表获取完整数据
```

### 8.5 会话切换流程

```
用户点击侧栏会话
    ↓
session.select(sessionId)
    ↓
路由更新 → /chat/{sessionId}
    ↓
WorkspaceView watch sessionId
    ↓
chat.resumeFromSession(sessionId)
    ↓
┌─ 清理旧状态 ────────────────────────────┐
│ stopPolling()                           │
│ currentRun = null                       │
│ runIdByExec = {}                        │
└─────────────────────────────────────────┘
    ↓
fetchMessages(sessionId)
    ↓
检查是否有未完成的 assistant 消息
    ├── 有 → getRunByExecution() 恢复轮询
    └── 无 → 完成
```

### 8.6 文件上传流程

```
用户选择文件（拖拽/粘贴/选择）
    ↓
显示 PendingAttachment (status: uploading)
    ↓
POST /api/chat/uploads (FormData)
    ↓
├── 成功 → 更新为真实 ID，status: done
└── 失败 → 显示错误，status: error
    ↓
发送消息时携带 attachmentIds
```

---

## 9. UI/UX 设计规范

### 9.1 设计原则

- **任务优先**：打开即能开始任务，不放大面积营销 Hero
- **选择明确**：新对话首屏直接展示助手列表
- **通用模型兜底**：没有合适助手时提供「使用通用对话」
- **过程透明但不过载**：默认只展示用户状态，处理信息按需查看
- **结果可操作**：支持复制、下载、重试、继续对话
- **人工介入清晰**：`waiting` 必须明显区别于失败

### 9.2 术语规范

| 用户术语 | 对应后端概念 | 禁止在 UI 出现 |
|----------|--------------|----------------|
| 助手 | Agent / Published Workflow | Workflow、Agent、Published |
| 对话 | Session | - |
| 处理状态 | Run.status | Run、Execution、RUNNING 等枚举 |
| 需要确认 | waiting / HITL | HITL、waiting、WAITING_INPUT |
| 处理信息 | run 节点/耗时 | node、timeline、思考链 |

### 9.3 色彩规范

```css
:root {
  /* 主色 */
  --c-primary: #0d6b67;        /* 深青绿 - 主操作、执行 */
  --c-primary-light: #9fd9d2;  /* 浅青绿 - 高光、流光 */
  --c-accent: #f59e0b;         /* 暖橙 - 强调、助手 */

  /* 状态色 */
  --c-success: #10b981;        /* 成功 */
  --c-warning: #f59e0b;        /* 警告、等待确认 */
  --c-error: #ef4444;          /* 错误 */
  --c-info: #3b82f6;           /* 信息 */

  /* 中性色 */
  --c-text: #1f2937;           /* 主文本 */
  --c-text-secondary: #6b7280; /* 次要文本 */
  --c-bg: #f8fafb;             /* 背景 */
  --c-surface: #ffffff;        /* 表面 */
  --c-border: #e5e7eb;         /* 边框 */
}
```

### 9.4 间距规范

```css
/* 间距 */
--space-xs: 4px;
--space-sm: 8px;
--space-md: 12px;
--space-lg: 16px;
--space-xl: 24px;
--space-2xl: 32px;

/* 圆角 */
--radius-sm: 4px;
--radius-md: 8px;
--radius-lg: 12px;
--radius-full: 9999px;
```

### 9.5 响应式断点

```css
/* 移动端 */
@media (max-width: 768px) {
  /* 侧栏折叠 */
  /* 全宽布局 */
  /* 触摸优化 */
}

/* 平板 */
@media (min-width: 769px) and (max-width: 1024px) {
  /* 侧栏可折叠 */
}

/* 桌面 */
@media (min-width: 1025px) {
  /* 固定侧栏 */
  /* 宽内容区 */
}
```

### 9.6 动画规范

```css
/* 流光动画（Composer 边框） */
@keyframes stream-cw {
  to { stroke-dashoffset: -100; }  /* 顺时针 2.8s */
}
@keyframes stream-ccw {
  to { stroke-dashoffset: 100; }   /* 逆时针 3.2s */
}

/* 减弱动画模式 */
@media (prefers-reduced-motion: reduce) {
  * { animation-duration: 0.01ms !important; }
}
```

---

## 10. 工具函数

### 10.1 messageContent.ts

```typescript
// 解析消息内容为结构化片段
function parseMessageContent(content: string): MessagePart[]

// 人性化运行时错误
function humanizeRuntimeError(content: string): string

// 检测是否为 HITL 批准回声
function isHitlApproveEcho(content: string): boolean
```

**错误人性化映射：**
- `approved: true` → 「已确认。若结果未更新，请稍候刷新...」
- `Workflow not found` → 「助手暂时不可用，请稍后重试...」
- 图片识别错误 → 「请上传 PNG/JPG 等图片，或改用文档类助手...」

### 10.2 textParser.ts

```typescript
// 文本解析工具
function parseMarkdown(text: string): string
function extractCodeBlocks(text: string): CodeBlock[]
function detectLanguage(code: string): string
```

---

## 11. 开发指南

### 11.1 本地开发

```bash
# 安装依赖
cd frontend && pnpm install

# 启动开发服务器
pnpm dev

# 构建生产版本
pnpm build

# 类型检查
vue-tsc -b

# 运行测试
pnpm test
```

### 11.2 环境变量

```bash
# .env.local
VITE_API_BASE_URL=/api                    # API 基础路径
VITE_TENANT_ID=                           # 租户 ID（开发用）
VITE_USER_ID=                             # 用户 ID（开发用）
```

### 11.3 组件开发规范

**命名规范：**
- 组件：PascalCase (`MessageBubble.vue`)
- 组合式函数：`use` 前缀 (`useAuthStore`)
- 工具函数：camelCase (`parseMessageContent`)
- 类型：PascalCase (`MessageStatus`)

**文件结构：**
```vue
<script setup lang="ts">
// 1. 导入
import { computed, ref } from 'vue'

// 2. Props/Emits
const props = defineProps<{ ... }>()
const emit = defineEmits<{ ... }>()

// 3. 状态
const loading = ref(false)

// 4. 计算属性
const canSubmit = computed(() => ...)

// 5. 方法
async function handleSubmit() { ... }
</script>

<template>
  <!-- 模板 -->
</template>

<style scoped>
/* 样式 */
</style>
```

### 11.4 Store 开发规范

```typescript
export const useXxxStore = defineStore('xxx', () => {
  // 1. State
  const data = ref(null)
  const loading = ref(false)
  const error = ref<string | null>(null)

  // 2. Computed
  const filtered = computed(() => ...)

  // 3. Actions
  async function fetch() {
    loading.value = true
    error.value = null
    try {
      data.value = await api.getXxx()
    } catch (e: any) {
      error.value = e.message
    } finally {
      loading.value = false
    }
  }

  // 4. Return
  return { data, loading, error, filtered, fetch }
})
```

### 11.5 API 对接规范

```typescript
// 1. 在 client.ts 添加 API 方法
export const api = {
  getXxx: () => request<XxxResponse>('/chat/xxx'),
  createXxx: (data: CreateXxxRequest) =>
    request<XxxResponse>('/chat/xxx', { method: 'POST', body: JSON.stringify(data) }),
}

// 2. 在 Store 中调用
async function fetchXxx() {
  const result = await api.getXxx()
  // 处理结果
}

// 3. 在组件中使用
const xxxStore = useXxxStore()
xxxStore.fetchXxx()
```

### 11.6 测试规范

```typescript
// 组件测试
import { mount } from '@vue/test-utils'
import MessageBubble from '../MessageBubble.vue'

describe('MessageBubble', () => {
  it('renders user message correctly', () => {
    const wrapper = mount(MessageBubble, {
      props: {
        message: { id: '1', role: 'user', content: 'Hello', status: 'COMPLETED' }
      }
    })
    expect(wrapper.text()).toContain('Hello')
  })
})

// 工具函数测试
import { parseMessageContent } from '../utils/messageContent'

describe('parseMessageContent', () => {
  it('parses markdown correctly', () => {
    const result = parseMessageContent('**bold**')
    expect(result).toContain('bold')
  })
})
```

---

## 附录：组件 Props/Events 速查表

| 组件 | Props | Events |
|------|-------|--------|
| Composer | disabled, placeholder | send(content, attachmentIds) |
| MessageBubble | message, run | - |
| ProcessingDrawer | open, run, agent, message, modelName, mode | close, cancel |
| ApprovalCard | waiting, sending | action(action, payload?) |
| SessionSidebar | - | navigate |
| AssistantPicker | - | select(agent) |
| ModelPicker | - | select(model) |
| ConversationHeader | - | - |
| MessageList | - | - |
| RunStatusBar | run | - |

---

*文档生成时间: 2026-08-14*
*基于代码版本: main branch @ 36457d2*
