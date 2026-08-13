<script setup lang="ts">
import type { ChatModel } from '../types'
defineProps<{ models: ChatModel[]; modelValue: string | null; loading: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
</script>
<template>
  <label class="model-picker">
    <el-select :model-value="modelValue || undefined" :disabled="loading || !models.length" placeholder="选择模型" @update:model-value="emit('update:modelValue', $event)">
      <el-option v-if="loading" label="正在加载模型…" value="" />
      <el-option v-else-if="!models.length" label="暂无可用模型" value="" />
      <el-option v-for="model in models" :key="model.id" :label="`${model.name} · ${model.provider}`" :value="model.id" />
    </el-select>
  </label>
</template>
<style scoped>
.model-picker { display: inline-flex; align-items: center; gap: 8px; color: var(--c-text-muted); font-size: 12px; }
.model-picker :deep(.el-select) { width: 240px; }
.model-picker :deep(.el-input__wrapper) { box-shadow: 0 0 0 1px var(--c-border) inset; border-radius: 6px; }
.model-picker :deep(.el-input__wrapper.is-focus) { box-shadow: 0 0 0 1px var(--c-primary) inset; }
</style>
