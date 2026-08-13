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
