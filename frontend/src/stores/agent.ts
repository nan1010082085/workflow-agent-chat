import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api } from '../api/client'
import type { Agent } from '../types'

export const useAgentStore = defineStore('agent', () => {
  const agents = ref<Agent[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function fetchAgents() {
    loading.value = true
    error.value = null
    try {
      const list = await api.listAgents()
      agents.value = list.map((a: any) => ({
        id: a.id,
        slug: a.slug,
        name: a.name,
        description: a.description,
        icon: a.icon,
        supportedInputs: a.supportedInputs || [],
        hitlCapable: !!a.hitlCapable,
        version: a.version,
        updatedAt: a.updatedAt,
        published: a.published !== false,
      }))
    } catch (e: any) {
      error.value = e.message || '获取 Agent 列表失败'
      agents.value = []
    } finally {
      loading.value = false
    }
  }

  function getAgent(id: string): Agent | undefined {
    return agents.value.find((a) => a.id === id || a.slug === id)
  }

  return { agents, loading, error, fetchAgents, getAgent }
})
