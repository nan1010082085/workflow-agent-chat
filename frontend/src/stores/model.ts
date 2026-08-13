import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api } from '../api/client'
import type { ChatModel } from '../types'

export const useModelStore = defineStore('model', () => {
  const models = ref<ChatModel[]>([])
  const selectedId = ref<string | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function fetchModels() {
    loading.value = true; error.value = null
    try {
      const result = await api.listModels()
      models.value = (result.items || []).map((m: any) => ({
        id: m.id, name: m.name || m.model, model: m.model, provider: m.provider || '平台模型',
        capabilities: m.capabilities || ['chat'], isDefault: !!m.isDefault,
      }))
      selectedId.value = result.defaultModelId || models.value[0]?.id || null
    } catch {
      models.value = []; error.value = '模型服务暂时不可用，请稍后重试'
    } finally { loading.value = false }
  }
  const selected = () => models.value.find((m) => m.id === selectedId.value) || null
  return { models, selectedId, loading, error, selected, fetchModels }
})
