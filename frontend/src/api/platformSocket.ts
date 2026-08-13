/**
 * 平台 Socket.IO 客户端：模型对话走 chat:send / chat:event（非 SSE）。
 * 生产与澄语同域，path 为 /schema-platform/ws。
 */
import { io, type Socket } from 'socket.io-client'
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

let socket: Socket | null = null
let tokenProvider: (() => string | null) | null = null

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
 * 确保已连接平台 Socket.IO。
 */
export function ensurePlatformSocket(): Promise<Socket> {
  const token = tokenProvider?.() || ''
  if (!token) {
    return Promise.reject(new Error('未登录，无法连接平台实时通道'))
  }

  if (socket?.connected) return Promise.resolve(socket)

  const { url, path } = resolveSocketTarget()
  if (!url) return Promise.reject(new Error('平台 WebSocket 地址未配置'))

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
  })

  return new Promise((resolve, reject) => {
    const s = socket!
    const timer = window.setTimeout(() => {
      cleanup()
      reject(new Error('连接平台实时通道超时'))
    }, 12_000)

    function cleanup() {
      window.clearTimeout(timer)
      s.off('connect', onConnect)
      s.off('connect_error', onError)
    }

    function onConnect() {
      cleanup()
      resolve(s)
    }

    function onError(err: Error) {
      cleanup()
      reject(err instanceof Error ? err : new Error(String(err)))
    }

    s.once('connect', onConnect)
    s.once('connect_error', onError)
  })
}

/** 组装注入平台 historySummary 的澄语上下文（助手目录 + 近期消息） */
export function buildChengyuHistorySummary(agents: Agent[], priorMessages: Message[]): string {
  const lines: string[] = [
    '【澄语产品说明】你正在「澄语」对话产品中。用户可选用基础模型，或切换到已发布的工作流助手。',
    '若用户询问有哪些智能体/助手/Agent，请根据下列已发布列表介绍，并引导其在界面中选择助手；不要声称系统没有智能体。',
  ]

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
  const historySummary = buildChengyuHistorySummary(input.agents, input.priorMessages)

  return new Promise((resolve, reject) => {
    let content = ''
    let thinking = ''
    let conversationId: string | null = input.conversationId || null
    let settled = false

    const onAbort = () => {
      s.emit('chat:cancel', {})
      finish({ content, thinking, conversationId, error: '已取消' })
    }

    function finish(result: StreamModelChatResult) {
      if (settled) return
      settled = true
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
      if (type === 'thinking_delta' && raw.content) {
        thinking += raw.content
      } else if (type === 'text_delta' && raw.content) {
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
