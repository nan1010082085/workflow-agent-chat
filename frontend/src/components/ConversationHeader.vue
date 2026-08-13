<script setup lang="ts">
import type { Agent } from '../types'

defineProps<{ agent: Agent | null; hasMessages: boolean; processing: boolean }>()
const emit = defineEmits<{
  changeAssistant: []
  toggleDetails: []
}>()
</script>

<template>
  <header class="conversation-header">
    <div class="conversation-identity">
      <span class="identity-icon">{{ agent?.icon || '✦' }}</span>
      <div class="identity-copy">
        <span class="identity-label">正在使用</span>
        <strong>{{ agent?.name || '选择一个助手开始' }}</strong>
      </div>
    </div>
    <div class="conversation-actions">
      <span v-if="processing" class="processing-label"><i></i>正在处理</span>
      <button class="text-action" type="button" @click="emit('changeAssistant')">
        {{ hasMessages ? '新建对话' : '选择助手' }}
      </button>
      <button v-if="hasMessages" class="text-action" type="button" @click="emit('toggleDetails')">
        处理详情
      </button>
    </div>
  </header>
</template>

<style scoped>
.conversation-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; min-height: 68px; padding: 12px 32px; border-bottom: 1px solid var(--c-border); background: var(--c-surface); }
.conversation-identity, .conversation-actions { display: flex; align-items: center; gap: 10px; min-width: 0; }
.identity-icon { display: grid; place-items: center; width: 34px; height: 34px; flex: none; border: 1px solid #f2d8b8; border-radius: 8px; background: var(--c-accent-soft); color: #b96d25; font-size: 17px; }
.identity-copy { display: grid; gap: 2px; min-width: 0; }
.identity-label { color: var(--c-text-muted); font-size: 11px; }
.identity-copy strong { overflow: hidden; color: var(--c-text); font-size: 14px; font-weight: 650; text-overflow: ellipsis; white-space: nowrap; }
.conversation-actions { flex: none; }
.text-action { border: 0; border-radius: 5px; background: transparent; color: var(--c-text-secondary); cursor: pointer; font-size: 12px; padding: 7px 8px; }
.text-action:hover { background: var(--c-bg); color: var(--c-primary); }
.processing-label { display: inline-flex; align-items: center; gap: 6px; color: var(--c-running); font-size: 12px; }
.processing-label i { width: 6px; height: 6px; border-radius: 50%; background: currentColor; }
@media (max-width: 767px) { .conversation-header { padding: 10px 16px; } .identity-label { display: none; } .conversation-actions { gap: 2px; } .processing-label { display: none; } }
</style>
