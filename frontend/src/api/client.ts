// API client：Bearer JWT + 可选开发态租户头；401 时尝试 refresh 一次。

import type { MessageAttachment } from '../types'

const BASE_URL = import.meta.env.VITE_API_BASE_URL
  || (import.meta.env.PROD ? `${import.meta.env.BASE_URL}api` : '/api')

let accessTokenProvider: (() => string | null) | null = null
let unauthorizedHandler: (() => void) | null = null
let refreshHandler: (() => Promise<boolean>) | null = null
let refreshing: Promise<boolean> | null = null

/** 注入 access token 读取器 */
export function setAccessTokenProvider(provider: () => string | null) {
  accessTokenProvider = provider
}

/** 注入 401 最终处理（清会话并跳登录） */
export function setUnauthorizedHandler(handler: (() => void) | null) {
  unauthorizedHandler = handler
}

/** 注入 refresh；返回 true 表示已换新 token */
export function setRefreshHandler(handler: (() => Promise<boolean>) | null) {
  refreshHandler = handler
}

export class ApiError extends Error {
  constructor(public code: string, message: string, public details?: string[]) {
    super(message)
    this.name = 'ApiError'
  }
}

/**
 * 附件内容 URL（带部署 base）。
 */
export function attachmentContentUrl(att: Pick<MessageAttachment, 'id' | 'url'>): string {
  return `${BASE_URL}/chat/uploads/${att.id}/content`
}

async function request<T>(path: string, options: RequestInit = {}, retry = true): Promise<T> {
  const url = path.startsWith('http') ? path : `${BASE_URL}${path}`
  const headers: Record<string, string> = {
    ...(options.headers as Record<string, string> || {}),
  }
  const token = accessTokenProvider?.()
  if (token) headers.Authorization = `Bearer ${token}`

  if (!(options.body instanceof FormData) && !headers['Content-Type'] && options.body) {
    headers['Content-Type'] = 'application/json'
  }

  const res = await fetch(url, { ...options, headers })
  if (res.status === 401 && retry && !path.includes('/auth/login') && !path.includes('/auth/refresh')) {
    const ok = await runRefresh()
    if (ok) return request<T>(path, options, false)
    unauthorizedHandler?.()
    throw new ApiError('UNAUTHORIZED', '请先登录')
  }
  if (!res.ok) {
    let body: any = null
    try { body = await res.json() } catch { /* ignore */ }
    const code = body?.code || `HTTP_${res.status}`
    const message = body?.message || userMessage(code, res.status)
    throw new ApiError(code, message, body?.details)
  }
  if (res.status === 204) return null as T
  if (!(res.headers.get('content-type') || '').includes('application/json')) {
    throw new ApiError('INVALID_API_RESPONSE', '接口地址配置错误，服务返回了网页而不是 JSON')
  }
  return res.json() as Promise<T>
}

async function runRefresh(): Promise<boolean> {
  if (!refreshHandler) return false
  if (!refreshing) {
    refreshing = refreshHandler().finally(() => { refreshing = null })
  }
  return refreshing
}

function userMessage(code: string, status: number): string {
  const messages: Record<string, string> = {
    UNAUTHORIZED: '请先登录',
    CATALOG_UNAVAILABLE: '智能体暂时不可用，请稍后重试',
    MODEL_LIST_UNAVAILABLE: '模型列表暂时不可用，请稍后重试',
    MODEL_UNAVAILABLE: '当前模型暂时不可用，请换一个模型',
    MODEL_RUNTIME_UNAVAILABLE: '当前模型暂时无法响应，请稍后重试',
    NETWORK_ERROR: '网络连接异常，请稍后重试',
    RATE_LIMITED: '请求过于频繁，请稍后再试',
    BAD_REQUEST: '请求无效，请检查文件或内容后重试',
  }
  if (code === 'RATE_LIMITED' || status === 429) return messages.RATE_LIMITED
  if (code === 'UNAUTHORIZED' || status === 401) return messages.UNAUTHORIZED
  return messages[code] || (status >= 500 ? '服务暂时不可用，请稍后重试' : '请求未完成，请稍后重试')
}


function parseJsonField(value: any): any {
  if (!value) return undefined;
  if (typeof value === 'string') {
    try { return JSON.parse(value); } catch { return undefined; }
  }
  return value;
}

export const api = {
  login: (data: { username: string; password: string; tenantCode?: string }) =>
    request<Record<string, unknown>>('/chat/auth/login', { method: 'POST', body: JSON.stringify(data) }, false),
  register: (data: { username: string; password: string; displayName?: string; phone?: string }) =>
    request<Record<string, unknown>>('/chat/auth/register', { method: 'POST', body: JSON.stringify(data) }, false),
  refresh: (refreshToken: string) =>
    request<Record<string, unknown>>('/chat/auth/refresh', {
      method: 'POST',
      body: JSON.stringify({ refreshToken }),
    }, false),
  me: () => request<Record<string, unknown>>('/chat/auth/me'),
  logout: () => request<null>('/chat/auth/logout', { method: 'POST' }),

  listModels: () => request<{ items: any[]; defaultModelId: string | null }>('/chat/models'),
  complete: (data: { modelId: string; messages: Array<{ role: string; content: string }> }) =>
    request<{ modelId: string; content: string }>('/chat/models/completions', { method: 'POST', body: JSON.stringify(data) }),
  listAgents: () => request<any[]>('/chat/agents'),

  listSessions: () => request<any[]>('/chat/sessions'),
  listAllSessions: async (page = 0, size = 50) => {
    const result = await request<{ sessions: any[]; total: number; page: number; size: number; totalPages: number }>(
      `/chat/sessions/all?page=${page}&size=${size}`
    );
    return {
      sessions: result.sessions.map((s: any) => ({
        id: s.id, title: s.title, agentId: s.agentId, agentName: s.agentName,
        platformConversationId: s.platformConversationId || null,
        status: s.status, createdAt: s.createdAt, updatedAt: s.updatedAt,
      })),
      total: result.total,
      page: result.page,
      size: result.size,
      totalPages: result.totalPages,
    };
  },

  createSession: (data: { title?: string; agentId?: string; agentName?: string }) =>
    request<any>('/chat/sessions', { method: 'POST', body: JSON.stringify(data) }),
  updateSessionTitle: (sessionId: string, title: string) =>
    request<any>(`/chat/sessions/${sessionId}/title`, { method: 'PATCH', body: JSON.stringify({ title }) }),

  listMessages: async (sessionId: string) => {
    const messages = await request<any[]>(`/chat/sessions/${sessionId}/messages`);
    return messages.map((m: any) => ({
      ...m,
      toolCalls: parseJsonField(m.toolCalls),
      documentSummaries: parseJsonField(m.documentSummaries),
      workflowExecution: parseJsonField(m.workflowExecution),
    }));
  },
  sendMessage: (sessionId: string, data: { agentId: string; content: string; attachmentIds?: string[] }) =>
    request<any>(`/chat/sessions/${sessionId}/messages`, { method: 'POST', body: JSON.stringify(data) }),
  completeInSession: (sessionId: string, data: { modelId: string; content: string; attachmentIds?: string[] }) =>
    request<any>(`/chat/sessions/${sessionId}/completions`, { method: 'POST', body: JSON.stringify(data) }),

  /**
   * 落库平台 WS 流式得到的模型回合（正文 + thinking）。
   */
  persistModelTurn: (sessionId: string, data: {
    modelId: string
    content: string
    attachmentIds?: string[]
    assistantContent: string
    thinking?: string
    platformConversationId?: string | null
    status?: string
  }) =>
    request<any>(`/chat/sessions/${sessionId}/model-turns`, { method: 'POST', body: JSON.stringify(data) }),

  /**
   * 上传附件到服务器（存盘路径由后端 CHAT_UPLOAD_ROOT 决定）。
   */
  uploadFile: async (file: File, sessionId?: string): Promise<MessageAttachment> => {
    const fd = new FormData()
    fd.append('file', file)
    if (sessionId) fd.append('sessionId', sessionId)
    return request<MessageAttachment>('/chat/uploads', { method: 'POST', body: fd })
  },

  getRun: (runId: string) => request<any>(`/chat/runs/${runId}`),
  /** 按 Runtime executionId 反查并同步 Chat run（刷新恢复 HITL） */
  getRunByExecution: (executionId: string) =>
    request<any>(`/chat/runs/by-execution/${encodeURIComponent(executionId)}`),
  resumeRun: (runId: string, data: { action: string; payload?: string }) =>
    request<any>(`/chat/runs/${runId}/resume`, { method: 'POST', body: JSON.stringify(data) }),
  cancelRun: (runId: string) =>
    request<any>(`/chat/runs/${runId}/cancel`, { method: 'POST' }),
}