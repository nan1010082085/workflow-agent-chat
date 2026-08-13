<script setup lang="ts">
import type { ChatModel } from '../types'
defineProps<{ models: ChatModel[]; modelValue: string | null; loading: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
</script>
<template>
  <label class="model-picker">
    <span>模型</span>
    <select :value="modelValue || ''" :disabled="loading || !models.length" @change="emit('update:modelValue', ($event.target as HTMLSelectElement).value)">
      <option v-if="loading" value="">正在加载模型…</option>
      <option v-else-if="!models.length" value="">暂无可用模型</option>
      <option v-for="model in models" :key="model.id" :value="model.id">{{ model.name }} · {{ model.provider }}</option>
    </select>
  </label>
</template>
<style scoped>
.model-picker { display: inline-flex; align-items: center; gap: 8px; color: var(--c-text-muted); font-size: 12px; }
select { max-width: 240px; border: 1px solid var(--c-border); border-radius: 6px; padding: 7px 28px 7px 10px; color: var(--c-text); background: var(--c-surface); outline: none; }
select:focus { border-color: var(--c-primary); }
</style>
