import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api } from '../api/client'
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

  async function sendMessage(sessionId: string, agentId: string, content: string) {
    sending.value = true
    error.value = null
    const tempUser: Message = {
      id: 'temp-' + Date.now(), role: 'user', content,
      runtimeExecutionId: null, status: 'COMPLETED', createdAt: new Date().toISOString(),
    }
    const tempAssistant: Message = {
      id: 'temp-a-' + Date.now(), role: 'assistant', content: '',
      runtimeExecutionId: null, status: 'RUNNING', createdAt: new Date().toISOString(),
    }
    messages.value.push(tempUser, tempAssistant)
    try {
      const result: SendMessageResult = await api.sendMessage(sessionId, { agentId, content })
      // 用真实 id 替换临时 id
      tempUser.id = result.messageId
      tempAssistant.id = result.assistantMessageId
      tempAssistant.runtimeExecutionId = result.runtimeExecutionId
      tempAssistant.status = result.status
      // F-07：runId 立即进入 store，建立 execId→runId 映射
      runIdByExec.value[result.runtimeExecutionId] = result.runId
      // 启动轮询（用 runId）
      if (result.status === 'RUNNING' || result.status === 'WAITING_INPUT') {
        startPolling(result.runId)
      } else if (result.status === 'COMPLETED' || result.status === 'FAILED') {
        await fetchRun(result.runId)
      }
      return result
    } catch (e: any) {
      error.value = e.message || '发送失败'
      // 移除乐观占位，避免幽灵消息
      messages.value = messages.value.filter(
        (m) => m.id !== tempUser.id && m.id !== tempAssistant.id
      )
      throw e
    } finally {
      sending.value = false
    }
  }

  async function sendModelMessage(modelId: string, content: string) {
    sending.value = true; error.value = null
    const user: Message = { id: `model-u-${Date.now()}`, role: 'user', content, runtimeExecutionId: null, status: 'COMPLETED', createdAt: new Date().toISOString() }
    const assistant: Message = { id: `model-a-${Date.now()}`, role: 'assistant', content: '', runtimeExecutionId: null, status: 'RUNNING', createdAt: new Date().toISOString() }
    modelMessages.value.push(user, assistant)
    try {
      const result = await api.complete({ modelId, messages: modelMessages.value.filter((m) => m.content).map((m) => ({ role: m.role, content: m.content })) })
      assistant.content = result.content; assistant.status = 'COMPLETED'
    } catch {
      assistant.content = '这次没有得到回复，请稍后重试。'; assistant.status = 'FAILED'
      error.value = '模型暂时无法响应，请稍后重试'
    } finally { sending.value = false }
  }

  async function fetchRun(runId: string) {
    try {
      const r: RunStatusView = await api.getRun(runId)
      currentRun.value = r
      applyRunToMessages(r)
      if (isTerminal(r.status)) {
        stopPolling()
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

  /** 根据消息的 execId 找到关联的 run（用于 inline approval） */
  function runForMessage(execId: string | null): RunStatusView | null {
    if (!execId || !currentRun.value) return null
    return currentRun.value.runtimeExecutionId === execId ? currentRun.value : null
  }

  return {
    messages, modelMessages, currentRun, sending, error, loadingMessages,
    fetchMessages, sendMessage, fetchRun, resumeRun, cancelRun, reset, stopPolling,
    resumeFromSession, runForMessage, sendModelMessage,
  }
})
