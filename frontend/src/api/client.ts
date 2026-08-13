// API client。基于 fetch，统一处理 base url、租户头、loading/error。
// 后端地址通过 VITE_API_BASE_URL 配置；租户头 X-Tenant-Id / X-User-Id 在开发态注入。

// Resolve API relative to the deployed Vite base path.
const BASE_URL = import.meta.env.VITE_API_BASE_URL
  || (import.meta.env.PROD ? `${import.meta.env.BASE_URL}api` : '/api')
const TENANT_ID = import.meta.env.VITE_TENANT_ID || 'dev-tenant'
const USER_ID = import.meta.env.VITE_USER_ID || 'dev-user'

export class ApiError extends Error {
  constructor(public code: string, message: string, public details?: string[]) {
    super(message)
    this.name = 'ApiError'
  }
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const url = path.startsWith('http') ? path : `${BASE_URL}${path}`
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    'X-Tenant-Id': TENANT_ID,
    'X-User-Id': USER_ID,
    ...(options.headers as Record<string, string> || {}),
  }
  const res = await fetch(url, { ...options, headers })
  if (!res.ok) {
    let body: any = null
    try { body = await res.json() } catch { /* ignore */ }
    const code = body?.code || `HTTP_${res.status}`
    const message = userMessage(code, res.status)
    throw new ApiError(code, message, body?.details)
  }
  if (res.status === 204) return null as T
  if (!(res.headers.get('content-type') || '').includes('application/json')) {
    throw new ApiError('INVALID_API_RESPONSE', '接口地址配置错误，服务返回了网页而不是 JSON')
  }
  return res.json() as Promise<T>
}

function userMessage(code: string, status: number): string {
  const messages: Record<string, string> = {
    CATALOG_UNAVAILABLE: '专用能力暂时不可用，请稍后重试',
    MODEL_LIST_UNAVAILABLE: '模型列表暂时不可用，请稍后重试',
    MODEL_UNAVAILABLE: '当前模型暂时不可用，请换一个模型',
    MODEL_RUNTIME_UNAVAILABLE: '当前模型暂时无法响应，请稍后重试',
    NETWORK_ERROR: '网络连接异常，请稍后重试',
  }
  return messages[code] || (status >= 500 ? '服务暂时不可用，请稍后重试' : '请求未完成，请稍后重试')
}

export const api = {
  listModels: () => request<{ items: any[]; defaultModelId: string | null }>('/chat/models'),
  complete: (data: { modelId: string; messages: Array<{ role: string; content: string }> }) =>
    request<{ modelId: string; content: string }>('/chat/models/completions', { method: 'POST', body: JSON.stringify(data) }),
  // agents
  listAgents: () => request<any[]>('/chat/agents'),

  // sessions
  listSessions: () => request<any[]>('/chat/sessions'),
  createSession: (data: { title?: string; agentId?: string; agentName?: string }) =>
    request<any>('/chat/sessions', { method: 'POST', body: JSON.stringify(data) }),

  // messages
  listMessages: (sessionId: string) => request<any[]>(`/chat/sessions/${sessionId}/messages`),
  sendMessage: (sessionId: string, data: { agentId: string; content: string }) =>
    request<any>(`/chat/sessions/${sessionId}/messages`, { method: 'POST', body: JSON.stringify(data) }),

  // runs
  getRun: (runId: string) => request<any>(`/chat/runs/${runId}`),
  resumeRun: (runId: string, data: { action: string; payload?: string }) =>
    request<any>(`/chat/runs/${runId}/resume`, { method: 'POST', body: JSON.stringify(data) }),
  cancelRun: (runId: string) =>
    request<any>(`/chat/runs/${runId}/cancel`, { method: 'POST' }),
}
