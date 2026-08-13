import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { api } from '../api/client'
import type { SessionSummary } from '../types'

/**
 * 从首条用户输入生成侧栏标题（与后端 SessionService.titleFromContent 对齐）。
 */
export function titleFromContent(content: string): string {
  const text = (content || '').replace(/\s+/g, ' ').trim()
  if (!text) return '新会话'
  return text.length > 40 ? `${text.slice(0, 40)}…` : text
}

export const useSessionStore = defineStore('session', () => {
  const sessions = ref<SessionSummary[]>([])
  const currentSessionId = ref<string | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)

  const current = computed(() =>
    sessions.value.find((s) => s.id === currentSessionId.value) || null
  )

  async function fetchSessions() {
    loading.value = true
    error.value = null
    try {
      const list = await api.listSessions()
      sessions.value = list.map((s: any) => ({
        id: s.id, title: s.title, agentId: s.agentId, agentName: s.agentName,
        status: s.status, createdAt: s.createdAt, updatedAt: s.updatedAt,
      }))
    } catch (e: any) {
      error.value = e.message || '获取会话列表失败'
    } finally {
      loading.value = false
    }
  }

  async function createSession(agentId?: string, agentName?: string, title?: string) {
    const s = await api.createSession({ title, agentId, agentName })
    const summary: SessionSummary = {
      id: s.id, title: s.title, agentId: s.agentId, agentName: s.agentName,
      status: s.status, createdAt: s.createdAt, updatedAt: s.updatedAt,
    }
    sessions.value = [summary, ...sessions.value.filter((item) => item.id !== summary.id)]
    currentSessionId.value = summary.id
    return summary
  }

  /**
   * 发送消息后刷新侧栏：更新标题并置顶。
   */
  function bumpSession(sessionId: string, patch: Partial<Pick<SessionSummary, 'title' | 'updatedAt' | 'agentName' | 'agentId'>> = {}) {
    const idx = sessions.value.findIndex((s) => s.id === sessionId)
    if (idx < 0) return
    const next: SessionSummary = {
      ...sessions.value[idx],
      ...patch,
      updatedAt: patch.updatedAt || new Date().toISOString(),
    }
    const rest = sessions.value.filter((s) => s.id !== sessionId)
    sessions.value = [next, ...rest]
  }

  async function updateTitle(sessionId: string, title: string) {
    const s = await api.updateSessionTitle(sessionId, title)
    bumpSession(sessionId, { title: s.title, updatedAt: s.updatedAt })
    return s
  }

  function select(id: string) {
    currentSessionId.value = id
  }

  return {
    sessions, currentSessionId, current, loading, error,
    fetchSessions, createSession, select, bumpSession, updateTitle,
  }
})
