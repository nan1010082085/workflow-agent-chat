// 与后端 API 契约对齐的类型定义。见 ARCHITECTURE §3。

export interface Agent {
  id: string
  slug: string
  name: string
  description: string
  icon: string
  supportedInputs: string[]
  hitlCapable: boolean
  version: string
  updatedAt: string
  published: boolean
}

export interface ChatModel {
  id: string
  name: string
  model: string
  provider: string
  capabilities: string[]
  isDefault: boolean
}

export interface SessionSummary {
  id: string
  title: string
  agentId: string | null
  agentName: string | null
  status: string
  createdAt: string
  updatedAt: string
}

export type MessageRole = 'user' | 'assistant' | 'system'
export type MessageStatus = 'PENDING' | 'RUNNING' | 'WAITING_INPUT' | 'COMPLETED' | 'FAILED' | 'CANCELLED'

export interface Message {
  id: string
  role: MessageRole
  content: string
  runtimeExecutionId: string | null
  status: MessageStatus
  createdAt: string
  thinking?: string
  tip?: string
  toolCalls?: ToolCallInfo[]
  documentSummaries?: MessageDocumentSummary[]
  workflowExecution?: WorkflowMessageExecution
  attachments?: MessageAttachment[]
}

/** 消息附件（上传落盘后的元数据） */
export interface MessageAttachment {
  id: string
  filename: string
  mimetype: string
  size?: number
  excerpt?: string | null
  url?: string
  createdAt?: string
}

/** Composer 待发送附件（含本地预览） */
export interface PendingAttachment {
  id: string
  filename: string
  mimetype: string
  size: number
  status: 'uploading' | 'done' | 'error'
  error?: string
  previewUrl?: string
}

export interface ToolCallInfo {
  id?: string
  name: string
  arguments: Record<string, unknown>
  result?: unknown
  error?: string
}

export interface MessageDocumentSummary {
  documentId: string
  filename: string
  summary: string
  pageCount?: number
}

export interface WorkflowMessageExecution {
  executionId: string
  workflowId: string
  workflowName: string
  status: string
  nodeRecords?: Array<{ nodeId: string; nodeName: string; nodeType: string; status: string; startedAt?: string; finishedAt?: string; durationMs?: number }>
  durationMs?: number
  error?: string
}

export type RunStatus = 'RUNNING' | 'COMPLETED' | 'FAILED' | 'WAITING_INPUT' | 'CANCELLED'

export interface WaitingField {
  key: string
  label: string
  type: string
  options: string[]
}

export interface WaitingAction {
  action: string
  label: string
  style: string
}

export interface WaitingPayload {
  prompt: string
  fields: WaitingField[]
  actions: WaitingAction[]
  dangerous: boolean
}

export interface RunStatusView {
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

export interface SendMessageResult {
  messageId: string
  assistantMessageId: string
  runId: string
  runtimeExecutionId: string
  status: MessageStatus
  sessionTitle?: string
}

export interface ModelTurnResult {
  messageId: string
  assistantMessageId: string
  content: string
  thinking?: string | null
  status: MessageStatus
  sessionTitle?: string
}

export interface CreateSessionRequest {
  title?: string
  agentId?: string
  agentName?: string
}

export interface SendMessageRequest {
  agentId: string
  content: string
}
