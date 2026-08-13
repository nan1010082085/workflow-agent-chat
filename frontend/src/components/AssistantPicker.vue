<script setup lang="ts">
import { computed } from 'vue'
import { useAgentStore } from '../stores/agent'
import type { Agent } from '../types'

const props = defineProps<{ modelValue: string | null }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: string): void; (e: 'select', agent: Agent): void }>()

const agentStore = useAgentStore()
const agents = computed(() => agentStore.agents)

// 用户术语：能力标签用「文本/文件/需要确认」，不用 HITL
function caps(a: Agent): string[] {
  const caps: string[] = ['文本']
  if (a.supportedInputs?.includes('file')) caps.push('文件')
  if (a.hitlCapable) caps.push('需要确认')
  return caps
}

function pick(a: Agent) {
  emit('update:modelValue', a.id)
  emit('select', a)
}
</script>

<template>
<div class="picker">
    <div v-if="agentStore.loading" class="empty-state" style="padding: 24px;">
      <p>正在加载助手…</p>
    </div>
    <div v-else-if="agentStore.error" class="empty-state" style="padding: 24px;">
      <p>助手列表暂时不可用</p>
    </div>
    <div v-else-if="!agents.length" class="empty-state" style="padding: 24px;">
      <p>暂无可用助手</p>
    </div>
    <button
      v-for="a in agents" :key="a.id"
      type="button"
      class="agent-item" :class="{ active: props.modelValue === a.id }"
      @click="pick(a)"
    >
      <span class="icon">{{ a.icon || '✦' }}</span>
      <span class="info">
        <b>{{ a.name }}</b>
        <small>{{ a.description || '适合处理一类明确任务' }}</small>
        <span class="caps">
          <span v-for="c in caps(a)" :key="c" class="cap" :class="{ confirm: c === '需要确认' }">{{ c }}</span>
        </span>
        <span class="choose-state">{{ props.modelValue === a.id ? '已选择' : '开始使用' }}</span>
      </span>
    </button>
  </div>
</template>

<style scoped>
.picker { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; }
.agent-item { position: relative; display: flex; gap: 12px; width: 100%; min-height: 108px; text-align: left; border: 1px solid var(--c-border); background: var(--c-surface); padding: 14px; border-radius: var(--radius); cursor: pointer; color: var(--c-text); transition: border-color .15s ease, box-shadow .15s ease; }
.agent-item:hover { background: var(--c-bg); }
.agent-item.active { border-color: var(--c-primary); background: var(--c-primary-soft); color: var(--c-primary); box-shadow: 0 0 0 2px rgba(13, 107, 103, .08); }
.icon { flex: none; font-size: 22px; line-height: 1.4; color: var(--c-accent); }
.info b { display: block; font-size: 14px; font-weight: 600; }
.info small { display: block; font-size: 12px; color: var(--c-text-muted); margin-top: 3px; line-height: 1.4; }
.caps { display: flex; gap: 5px; margin-top: 6px; }
.cap { font-size: 10px; padding: 1px 6px; background: var(--c-bg); border-radius: 3px; color: var(--c-text-muted); }
.cap.confirm { color: var(--c-warning); background: #fdf2df; }
.choose-state { display: block; margin-top: 8px; color: var(--c-primary); font-size: 11px; font-weight: 650; }
@media (max-width: 600px) { .picker { grid-template-columns: 1fr; } }
</style>
