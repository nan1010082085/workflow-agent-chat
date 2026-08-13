import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { api } from '../api/client'
import type { SessionSummary } from '../types'

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
    sessions.value.unshift(summary)
    currentSessionId.value = summary.id
    return summary
  }

  function select(id: string) {
    currentSessionId.value = id
  }

  return { sessions, currentSessionId, current, loading, error, fetchSessions, createSession, select }
})
