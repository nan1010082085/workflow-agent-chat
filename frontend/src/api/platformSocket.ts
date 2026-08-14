/**
 * 平台 Socket.IO 客户端：模型对话走 chat:send / chat:event（非 SSE）。
 * 生产与澄语同域，path 为 /schema-platform/ws。
 */
import { io, type Socket } from 'socket.io-client'
import { computed, readonly, ref, shallowRef } from 'vue'
import type { Agent, Message } from '../types'

export interface PlatformChatEvent {
  threadId?: string
  type: string
  content?: string
  conversationId?: string
  agent?: string
  description?: string
  [key: string]: unknown
}

export interface StreamModelChatInput {
  message: string
  /** 平台 AIConversation id（Mongo ObjectId） */
  conversationId?: string | null
  /** 上游模型名，如 deepseek-chat */
  llmModel?: string
  agents: Agent[]
  /** 澄语侧近期消息，写入 historySummary 辅助多轮 */
  priorMessages: Message[]
  onEvent: (event: PlatformChatEvent) => void
  signal?: AbortSignal
}

export interface StreamModelChatResult {
  content: string
  thinking: string
  conversationId: string | null
  error?: string
}

/** 平台实时通道连接态 */
export type PlatformWsPhase =
  | 'idle'
  | 'connecting'
  | 'connected'
  | 'disconnected'
  | 'error'

const phase = shallowRef<PlatformWsPhase>('idle')
const detail = ref('')
const streaming = shallowRef(false)
const lastError = ref('')
const transport = ref('')

/** 只读连接状态（供 Composer 展示） */
export const platformWsState = readonly(phase)
export const platformWsDetail = readonly(detail)
export const platformWsStreaming = readonly(streaming)
export const platformWsLastError = readonly(lastError)
export const platformWsTransport = readonly(transport)

/**
 * 输入框底部展示用文案。
 */
export const platformWsLabel = computed(() => {
  if (streaming.value) return 'WS 流式输出中'
  switch (phase.value) {
    case 'connected':
      return transport.value ? `WS 已连接 · ${transport.value}` : 'WS 已连接'
    case 'connecting':
      return 'WS 连接中…'
    case 'disconnected':
      return 'WS 已断开'
    case 'error':
      return lastError.value ? `WS 异常 · ${lastError.value}` : 'WS 异常'
    default:
      return 'WS 未连接'
  }
})

/**
 * 状态点样式类名。
 */
export const platformWsTone = computed(() => {
  if (streaming.value) return 'streaming'
  switch (phase.value) {
    case 'connected':
      return 'ok'
    case 'connecting':
      return 'pending'
    case 'error':
      return 'err'
    case 'disconnected':
      return 'warn'
    default:
      return 'idle'
  }
})

let socket: Socket | null = null
let tokenProvider: (() => string | null) | null = null
let connectPromise: Promise<Socket> | null = null

/** 注入 JWT 提供者（与 Chat auth store 对齐） */
export function setPlatformSocketTokenProvider(provider: () => string | null): void {
  tokenProvider = provider
}

function resolveSocketTarget(): { url: string; path: string } {
  const url = (import.meta.env.VITE_PLATFORM_WS_URL as string | undefined)?.trim()
    || (typeof window !== 'undefined' ? window.location.origin : '')
  const path = (import.meta.env.VITE_PLATFORM_WS_PATH as string | undefined)?.trim()
    || '/schema-platform/ws'
  return { url, path }
}

/**
 * 绑定长生命周期监听，更新底部状态指示。
 * @param {Socket} s
 */
function bindLifecycle(s: Socket): void {
  s.off('connect', onConnected)
  s.off('disconnect', onDisconnected)
  s.off('connect_error', onConnectError)
  s.on('connect', onConnected)
  s.on('disconnect', onDisconnected)
  s.on('connect_error', onConnectError)
}

function onConnected(): void {
  phase.value = 'connected'
  detail.value = '平台实时通道已就绪'
  lastError.value = ''
  transport.value = socket?.io?.engine?.transport?.name || 'websocket'
}

function onDisconnected(reason: string): void {
  phase.value = 'disconnected'
  detail.value = reason || '连接已断开'
  streaming.value = false
}

function onConnectError(err: Error): void {
  phase.value = 'error'
  lastError.value = (err?.message || String(err)).slice(0, 80)
  detail.value = lastError.value
  streaming.value = false
}

/**
 * 确保已连接平台 Socket.IO。
 */
export function ensurePlatformSocket(): Promise<Socket> {
  const token = tokenProvider?.() || ''
  if (!token) {
    phase.value = 'error'
    lastError.value = '未登录'
    return Promise.reject(new Error('未登录，无法连接平台实时通道'))
  }

  if (socket?.connected) {
    phase.value = 'connected'
    return Promise.resolve(socket)
  }

  if (connectPromise) return connectPromise

  const { url, path } = resolveSocketTarget()
  if (!url) {
    phase.value = 'error'
    lastError.value = '地址未配置'
    return Promise.reject(new Error('平台 WebSocket 地址未配置'))
  }

  phase.value = 'connecting'
  detail.value = `${path}`

  if (socket) {
    socket.removeAllListeners()
    socket.disconnect()
    socket = null
  }

  socket = io(url, {
    path,
    transports: ['websocket', 'polling'],
    auth: { token },
    autoConnect: true,
    reconnection: true,
    reconnectionAttempts: 8,
    reconnectionDelay: 800,
  })
  bindLifecycle(socket)

  connectPromise = new Promise((resolve, reject) => {
    const s = socket!
    const timer = window.setTimeout(() => {
      cleanup()
      connectPromise = null
      phase.value = 'error'
      lastError.value = '连接超时'
      reject(new Error('连接平台实时通道超时'))
    }, 12_000)

    function cleanup() {
      window.clearTimeout(timer)
      s.off('connect', onConnectOnce)
      s.off('connect_error', onErrorOnce)
    }

    function onConnectOnce() {
      cleanup()
      connectPromise = null
      onConnected()
      resolve(s)
    }

    function onErrorOnce(err: Error) {
      cleanup()
      connectPromise = null
      onConnectError(err)
      reject(err instanceof Error ? err : new Error(String(err)))
    }

    s.once('connect', onConnectOnce)
    s.once('connect_error', onErrorOnce)
  })

  return connectPromise
}

/**
 * 进入工作区时预热连接（不阻塞 UI）。
 */
export function warmPlatformSocket(): void {
  if (!tokenProvider?.()) {
    phase.value = 'idle'
    detail.value = '登录后连接'
    return
  }
  void ensurePlatformSocket().catch(() => {
    /* 底部状态已更新 */
  })
}

/**
 * 手动重连。
 */
export function reconnectPlatformSocket(): void {
  if (socket) {
    socket.removeAllListeners()
    socket.disconnect()
    socket = null
  }
  connectPromise = null
  void ensurePlatformSocket().catch(() => {
    /* 底部状态已更新 */
  })
}

/** 组装注入平台 historySummary 的澄语上下文（助手目录 + 近期消息） */
export function buildChengyuHistorySummary(
  agents: Agent[],
  priorMessages: Message[],
  options?: { modelName?: string | null },
): string {
  const lines: string[] = [
    '【澄语产品说明】你正在「澄语」对话产品中。用户可选用基础模型，或切换到已发布的工作流助手。',
    '【身份规则】对外身份是「澄语」助手；不要自称 schema-platform、基础平台或其他底层平台品牌；不要解释底层实现。',
    '若用户询问「你是谁」，回答你是澄语助手即可。',
    '若用户询问有哪些智能体/助手/Agent，请根据下列已发布列表介绍，并引导其在界面中选择助手；不要声称系统没有智能体；不要扯平台实现。',
  ]

  const modelName = options?.modelName?.trim()
  if (modelName) {
    lines.push(`若用户询问所用模型，可告知当前选用模型为「${modelName}」。`)
  } else {
    lines.push('若用户询问所用模型且未指定具体模型，请如实说明模型名称未知，不要编造平台品牌。')
  }

  const published = agents.filter((a) => a.published !== false)
  if (published.length === 0) {
    lines.push('【当前租户已发布助手】暂无；可如实告知并建议稍后刷新列表。')
  } else {
    lines.push('【当前租户已发布助手】')
    published.forEach((a, i) => {
      const desc = a.description?.trim() ? ` — ${a.description.trim()}` : ''
      lines.push(`${i + 1}. ${a.name}${a.slug ? `（${a.slug}）` : ''}${desc}`)
    })
  }

  const recent = priorMessages
    .filter((m) => m.status === 'COMPLETED' && m.content?.trim())
    .slice(-8)
  if (recent.length) {
    lines.push('--- 澄语会话近期消息 ---')
    for (const m of recent) {
      const role = m.role === 'assistant' ? '助手' : '用户'
      lines.push(`${role}: ${m.content.slice(0, 800)}`)
    }
  }

  return lines.join('\n')
}

/**
 * 经平台 chat:send 流式一轮模型对话，收集 text_delta / thinking_delta。
 */
export async function streamModelChatViaPlatform(input: StreamModelChatInput): Promise<StreamModelChatResult> {
  const s = await ensurePlatformSocket()
  const historySummary = buildChengyuHistorySummary(input.agents, input.priorMessages, {
    modelName: input.llmModel,
  })
  streaming.value = true

  return new Promise((resolve, reject) => {
    let content = ''
    let thinking = ''
    let conversationId: string | null = input.conversationId || null
    let settled = false
    let gotDelta = false

    const onAbort = () => {
      s.emit('chat:cancel', {})
      finish({ content, thinking, conversationId, error: '已取消' })
    }

    function finish(result: StreamModelChatResult) {
      if (settled) return
      settled = true
      streaming.value = false
      cleanup()
      if (result.error && !result.content && !result.thinking) {
        reject(new Error(result.error))
      } else {
        resolve(result)
      }
    }

    function onEvent(raw: PlatformChatEvent) {
      input.onEvent(raw)
      const type = raw.type
      if (type === 'agent_switch' && raw.agent) {
        detail.value = `专家 · ${String(raw.agent)}`
      } else if (type === 'task_progress' && raw.description) {
        detail.value = String(raw.description).slice(0, 48)
      } else if (type === 'thinker_start') {
        detail.value = '路由分析中'
      } else if (type === 'text_delta' || type === 'thinking_delta') {
        detail.value = '正在生成回复'
      }
      if (type === 'thinking_delta' && raw.content) {
        gotDelta = true
        thinking += raw.content
      } else if (type === 'text_delta' && raw.content) {
        gotDelta = true
        content += raw.content
      } else if (type === 'error') {
        finish({
          content,
          thinking,
          conversationId,
          error: String(raw.content || '模型流式响应失败'),
        })
      } else if (type === 'done') {
        const cid = raw.conversationId
        if (cid != null && String(cid)) {
          conversationId = String(cid)
        }
        if (!gotDelta && !content && !thinking) {
          finish({
            content,
            thinking,
            conversationId,
            error: 'WS 已连通但未收到流式增量',
          })
          return
        }
        finish({ content, thinking, conversationId })
      }
    }

    function cleanup() {
      s.off('chat:event', onEvent)
      s.off('chat:error', onSocketError)
      input.signal?.removeEventListener('abort', onAbort)
    }

    function onSocketError(payload: { error?: string }) {
      finish({
        content,
        thinking,
        conversationId,
        error: payload?.error || '平台实时通道错误',
      })
    }

    input.signal?.addEventListener('abort', onAbort)
    s.on('chat:event', onEvent)
    s.on('chat:error', onSocketError)

    s.emit('chat:send', {
      ...(input.conversationId ? { conversationId: input.conversationId } : {}),
      message: input.message,
      context: {
        source: 'standalone',
        preferences: {
          product: 'workflow-agent-chat',
          replyLanguage: 'zh-CN',
          ...(input.llmModel ? { llmModel: input.llmModel } : {}),
        },
        historySummary,
      },
    })
  })
}
