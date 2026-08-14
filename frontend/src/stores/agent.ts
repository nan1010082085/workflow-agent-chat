import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api } from '../api/client'
import type { Agent } from '../types'

/**
 * 文档/图片类 slug 在 Catalog 未声明 file 时的前端兜底能力。
 * @param {string} slug
 * @param {string[]} inputs
 * @returns {string[]}
 */
function enrichSupportedInputs(slug: string, inputs: string[]): string[] {
  const set = new Set(inputs?.length ? inputs : ['text'])
  const s = (slug || '').toLowerCase()
  const needsFile = /document|contract|resume|expense|multi-doc|doc-image|image|pdf|ocr|vision/.test(s)
  const needsImage = /image|vision|ocr|doc-image/.test(s)
  if (needsFile) set.add('file')
  if (needsImage) {
    set.add('image')
    set.add('file')
  }
  return [...set]
}

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
        supportedInputs: enrichSupportedInputs(a.slug || '', a.supportedInputs || []),
        hitlCapable: !!a.hitlCapable,
        version: a.version,
        updatedAt: a.updatedAt,
        published: a.published !== false,
      }))
    } catch (e: any) {
      error.value = e.message || '获取 Agent 列表失败'
    } finally {
      loading.value = false
    }
  }

  function getAgent(id: string): Agent | undefined {
    return agents.value.find((a) => a.id === id || a.slug === id)
  }

  return { agents, loading, error, fetchAgents, getAgent }
})
