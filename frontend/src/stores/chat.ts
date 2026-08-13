import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api } from '../api/client'
import { streamModelChatViaPlatform } from '../api/platformSocket'
import { useAgentStore } from './agent'
import { useModelStore } from './model'
import { useSessionStore } from './session'
import type { Message, RunStatusView, SendMessageResult } from '../types'

/**
 * Chat store。管理当前会话消息 + 当前 run + 轮询。
 * F-07：发送响应的 runId 立即进入 store；轮询用 runId；切换会话停止旧 polling；刷新可恢复。
 * 术语遵循产品基线：助手/对话/处理状态/需要确认/处理信息。
 */
export const useChatStore = defineStore('chat', () => {
  const messages = ref<Message[]>([])
  const currentRun = ref<RunStatusView | null>(null)
  const sending = ref(false)
  const error = ref<string | null>(null)
  const loadingMessages = ref(false)
  const modelMessages = ref<Message[]>([])

  let pollTimer: ReturnType<typeof setTimeout> | null = null
  // key: runtimeExecutionId, value: runId —— 用于把 waiting/run 关联到消息
  const runIdByExec = ref<Record<string, string>>({})

  async function fetchMessages(sessionId: string) {
    loadingMessages.value = true
    error.value = null
    try {
      const list = await api.listMessages(sessionId)
      messages.value = list.map((m: any) => ({
        id: m.id, role: m.role, content: m.content,
        runtimeExecutionId: m.runtimeExecutionId,
        status: m.status, createdAt: m.createdAt,
        thinking: m.thinking, tip: m.tip, toolCalls: m.toolCalls,
        documentSummaries: m.documentSummaries, workflowExecution: m.workflowExecution,
        attachments: m.attachments || [],
      }))
      // 刷新恢复：若有未终结的 assistant 消息，恢复轮询
      const pending = messages.value.find(
        (m) => m.role === 'assistant' &&
          (m.status === 'RUNNING' || m.status === 'WAITING_INPUT') &&
          m.runtimeExecutionId
      )
      if (pending?.runtimeExecutionId) {
        // 无法从消息直接拿 runId，需后端按 executionId 反查；暂用 execId 作 key 轮询
        // 实际 runId 在发送时已存入 runIdByExec；刷新后丢失，需后端提供。
        // 折中：若 currentRun 为空则尝试用 execId 触发一次同步（后端 getRun 需 runId）
        // 此处保留 pending 标记，由组件按需处理
      }
    } catch (e: any) {
      error.value = e.message || '获取消息失败'
    } finally {
      loadingMessages.value = false
    }
  }

  async function sendMessage(
    sessionId: string,
    agentId: string,
    content: string,
    attachmentIds: string[] = [],
  ) {
    sending.value = true
    error.value = null
    const tempUserId = 'temp-' + Date.now()
    const tempAssistantId = 'temp-a-' + Date.now()
    messages.value.push(
      {
        id: tempUserId, role: 'user', content: content || (attachmentIds.length ? '（见附件）' : ''),
        runtimeExecutionId: null, status: 'COMPLETED', createdAt: new Date().toISOString(),
        attachments: attachmentIds.map((id) => ({ id, filename: '附件', mimetype: 'application/octet-stream' })),
      },
      {
        id: tempAssistantId, role: 'assistant', content: '',
        runtimeExecutionId: null, status: 'RUNNING', createdAt: new Date().toISOString(),
      },
    )
    const userIndex = messages.value.length - 2
    const assistantIndex = messages.value.length - 1
    try {
      const result: SendMessageResult = await api.sendMessage(sessionId, {
        agentId,
        content,
        attachmentIds,
      })
      const user = messages.value[userIndex]
      const assistant = messages.value[assistantIndex]
      if (user) user.id = result.messageId
      if (assistant) {
        assistant.id = result.assistantMessageId
        assistant.runtimeExecutionId = result.runtimeExecutionId
        assistant.status = result.status
      }
      runIdByExec.value[result.runtimeExecutionId] = result.runId
      if (result.status === 'RUNNING' || result.status === 'WAITING_INPUT') {
        startPolling(result.runId)
      } else if (result.status === 'COMPLETED' || result.status === 'FAILED') {
        await fetchRun(result.runId)
      }
      // 回拉消息以拿到完整附件元数据
      await fetchMessages(sessionId)
      return result
    } catch (e: any) {
      error.value = e.message || '发送失败'
      messages.value = messages.value.filter(
        (m) => m.id !== tempUserId && m.id !== tempAssistantId
      )
      throw e
    } finally {
      sending.value = false
    }
  }

  async function sendModelMessage(
    sessionId: string,
    modelId: string,
    content: string,
    attachmentIds: string[] = [],
  ) {
    sending.value = true
    error.value = null
    const tempUserId = `model-u-${Date.now()}`
    const tempAssistantId = `model-a-${Date.now()}`
    const priorMessages = messages.value.slice()
    messages.value.push(
      {
        id: tempUserId, role: 'user',
        content: content || (attachmentIds.length ? '（见附件）' : ''),
        runtimeExecutionId: null, status: 'COMPLETED', createdAt: new Date().toISOString(),
        attachments: attachmentIds.map((id) => ({ id, filename: '附件', mimetype: 'application/octet-stream' })),
      },
      { id: tempAssistantId, role: 'assistant', content: '', runtimeExecutionId: null, status: 'RUNNING', createdAt: new Date().toISOString() },
    )
    const userIndex = messages.value.length - 2
    const assistantIndex = messages.value.length - 1

    const agentStore = useAgentStore()
    const modelStore = useModelStore()
    const sessionStore = useSessionStore()
    if (!agentStore.agents.length) {
      try { await agentStore.fetchAgents() } catch { /* 目录失败时仍可对话 */ }
    }
    const selectedModel = modelStore.selected()
    const llmModel = selectedModel?.model || selectedModel?.name
    const platformConversationId = sessionStore.current?.platformConversationId || null

    try {
      let streamedContent = ''
      let streamedThinking = ''
      let nextPlatformConvo: string | null = platformConversationId
      let usedWs = false

      try {
        const stream = await streamModelChatViaPlatform({
          message: content || (attachmentIds.length ? '（见附件）' : ''),
          conversationId: platformConversationId,
          llmModel,
          agents: agentStore.agents,
          priorMessages,
          onEvent: (ev) => {
            const assistant = messages.value[assistantIndex]
            if (!assistant) return
            if (ev.type === 'thinking_delta' && ev.content) {
              assistant.thinking = `${assistant.thinking || ''}${ev.content}`
            }
            if (ev.type === 'text_delta' && ev.content) {
              assistant.content = `${assistant.content || ''}${ev.content}`
            }
          },
        })
        usedWs = true
        streamedContent = stream.content
        streamedThinking = stream.thinking
        nextPlatformConvo = stream.conversationId || platformConversationId
        if (stream.error && !streamedContent) {
          throw new Error(stream.error)
        }
      } catch (wsErr: any) {
        const partial = messages.value[assistantIndex]
        const hasPartial = Boolean(partial?.content?.trim() || partial?.thinking?.trim())
        if (hasPartial) {
          console.warn('[chat] 平台 WS 中断，保留已流式内容', wsErr?.message || wsErr)
          usedWs = true
          streamedContent = partial?.content || ''
          streamedThinking = partial?.thinking || ''
        } else {
          console.warn('[chat] 平台 WS 流式失败，回退同步补全', wsErr?.message || wsErr)
          const result = await api.completeInSession(sessionId, { modelId, content, attachmentIds })
          const user = messages.value[userIndex]
          const assistant = messages.value[assistantIndex]
          if (user) user.id = result.messageId
          if (assistant) {
            assistant.id = result.assistantMessageId
            assistant.content = result.content || '（模型返回了空内容）'
            assistant.thinking = result.thinking || undefined
            assistant.status = result.status || 'COMPLETED'
          }
          if (result.platformConversationId) {
            sessionStore.bumpSession(sessionId, { platformConversationId: result.platformConversationId })
          }
          await fetchMessages(sessionId)
          return result as {
            sessionTitle?: string
            content: string
            thinking?: string
            status: string
            platformConversationId?: string
          }
        }
      }

      if (usedWs) {
        const assistant = messages.value[assistantIndex]
        if (assistant) {
          assistant.content = streamedContent || assistant.content || '（模型返回了空内容）'
          assistant.thinking = streamedThinking || assistant.thinking || undefined
          assistant.status = streamedContent ? 'COMPLETED' : 'FAILED'
        }
        const result = await api.persistModelTurn(sessionId, {
          modelId,
          content,
          attachmentIds,
          assistantContent: streamedContent || (messages.value[assistantIndex]?.content ?? ''),
          thinking: streamedThinking || undefined,
          platformConversationId: nextPlatformConvo,
          status: streamedContent ? 'COMPLETED' : 'FAILED',
        })
        const user = messages.value[userIndex]
        if (user) user.id = result.messageId
        if (assistant) {
          assistant.id = result.assistantMessageId
          assistant.content = result.content || assistant.content
          assistant.thinking = result.thinking || assistant.thinking
          assistant.status = result.status || assistant.status
        }
        if (result.platformConversationId) {
          sessionStore.bumpSession(sessionId, { platformConversationId: result.platformConversationId })
        }
        await fetchMessages(sessionId)
        return result as {
          sessionTitle?: string
          content: string
          thinking?: string
          status: string
          platformConversationId?: string
        }
      }

      throw new Error('模型未返回结果')
    } catch (e: any) {
      const assistant = messages.value[assistantIndex]
      if (assistant) {
        assistant.content = assistant.content || '这次没有得到回复，请稍后重试。'
        assistant.status = 'FAILED'
      }
      error.value = e?.message || '模型暂时无法响应，请稍后重试'
      throw e
    } finally {
      sending.value = false
    }
  }

  async function fetchRun(runId: string) {
    try {
      const r: RunStatusView = await api.getRun(runId)
      currentRun.value = r
      applyRunToMessages(r)
      if (isTerminal(r.status)) {
        stopPolling()
        // 后端已把结果写入 assistant message，终态时回拉消息以展示正文
        if (r.sessionId) await fetchMessages(r.sessionId)
      } else if (!pollTimer) {
        startPolling(runId)
      }
      return r
    } catch (e: any) {
      error.value = e.message || '查询处理状态失败'
      return null
    }
  }

  function applyRunToMessages(run: RunStatusView) {
    const idx = messages.value.findIndex((m) => m.runtimeExecutionId === run.runtimeExecutionId)
    if (idx < 0) return
    const m = messages.value[idx]
    const map: Record<string, Message['status']> = {
      COMPLETED: 'COMPLETED', FAILED: 'FAILED', WAITING_INPUT: 'WAITING_INPUT',
      CANCELLED: 'CANCELLED', RUNNING: 'RUNNING',
    }
    if (map[run.status]) m.status = map[run.status]
  }

  function startPolling(runId: string) {
    stopPolling()
    const tick = async () => {
      const r = await fetchRun(runId)
      if (r && !isTerminal(r.status)) {
        pollTimer = setTimeout(tick, 2000)
      }
    }
    pollTimer = setTimeout(tick, 1500)
  }

  function stopPolling() {
    if (pollTimer) {
      clearTimeout(pollTimer)
      pollTimer = null
    }
  }

  /** 切换会话时停止旧 polling（F-07） */
  async function resumeFromSession(sessionId: string) {
    stopPolling()
    currentRun.value = null
    runIdByExec.value = {}
    await fetchMessages(sessionId)
  }

  async function resumeRun(runId: string, action: string, payload?: string) {
    sending.value = true
    try {
      const r: RunStatusView = await api.resumeRun(runId, { action, payload })
      currentRun.value = r
      applyRunToMessages(r)
      if (!isTerminal(r.status)) startPolling(runId)
      return r
    } catch (e: any) {
      error.value = e.message || '提交失败'
      return null
    } finally {
      sending.value = false
    }
  }

  async function cancelRun(runId: string) {
    try {
      const r: RunStatusView = await api.cancelRun(runId)
      currentRun.value = r
      applyRunToMessages(r)
      stopPolling()
      return r
    } catch (e: any) {
      error.value = e.message || '取消失败'
      return null
    }
  }

  function isTerminal(status: RunStatusView['status']) {
    return status === 'COMPLETED' || status === 'FAILED' || status === 'CANCELLED'
  }

  function reset() {
    messages.value = []
    currentRun.value = null
    error.value = null
    runIdByExec.value = {}
    stopPolling()
    modelMessages.value = []
  }

  function clearAgentConversation() {
    messages.value = []
    currentRun.value = null
    error.value = null
    runIdByExec.value = {}
    stopPolling()
  }

  /** 根据消息的 execId 找到关联的 run（用于 inline approval） */
  function runForMessage(execId: string | null): RunStatusView | null {
    if (!execId || !currentRun.value) return null
    return currentRun.value.runtimeExecutionId === execId ? currentRun.value : null
  }

  return {
    messages, modelMessages, currentRun, sending, error, loadingMessages,
    fetchMessages, sendMessage, fetchRun, resumeRun, cancelRun, reset, stopPolling,
    resumeFromSession, runForMessage, sendModelMessage, clearAgentConversation,
  }
})
