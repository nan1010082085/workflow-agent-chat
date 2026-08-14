<script setup lang="ts">
import { computed } from 'vue'
import { useAgentStore } from '../stores/agent'
import type { Agent } from '../types'
import AppMark from './AppMark.vue'

const props = defineProps<{ modelValue: string | null }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: string): void; (e: 'select', agent: Agent): void }>()

const agentStore = useAgentStore()
const agents = computed(() => agentStore.agents)

/** 用户术语：能力标签用「文本/文件/需要确认」，不用 HITL */
function caps(a: Agent): string[] {
  const list: string[] = ['文本']
  if (a.supportedInputs?.includes('file')) list.push('文件')
  if (a.supportedInputs?.includes('image')) list.push('图片')
  if (a.hitlCapable) list.push('需要确认')
  return list
}

function pick(a: Agent) {
  emit('update:modelValue', a.id)
  emit('select', a)
}
</script>

<template>
  <div class="picker">
    <div v-if="agentStore.loading" class="empty-state" style="padding: 24px;">
      <p>正在加载智能体…</p>
    </div>
    <div v-else-if="agentStore.error" class="empty-state" style="padding: 24px;">
      <p>智能体列表暂时不可用</p>
    </div>
    <div v-else-if="!agents.length" class="empty-state" style="padding: 24px;">
      <p>暂无可用智能体</p>
    </div>
    <button
      v-for="a in agents"
      :key="a.id"
      type="button"
      class="agent-item"
      :class="{ active: props.modelValue === a.id }"
      @click="pick(a)"
    >
      <div class="agent-main">
        <AppMark variant="ai" size="md" :glyph="a.icon || ''" />
        <span class="info">
          <b>{{ a.name }}</b>
          <small>{{ a.description || '适合处理一类明确任务' }}</small>
          <span class="caps">
            <span
              v-for="c in caps(a)"
              :key="c"
              class="cap"
              :class="{ confirm: c === '需要确认' }"
            >{{ c }}</span>
          </span>
        </span>
      </div>
      <span class="choose-state" :class="{ selected: props.modelValue === a.id }">
        {{ props.modelValue === a.id ? '已选择' : '开始使用' }}
      </span>
    </button>
  </div>
</template>

<style scoped>
.picker {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  align-content: start;
}
.agent-item {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
  min-width: 0;
  min-height: 148px;
  padding: 14px;
  text-align: left;
  border: 1px solid var(--c-border);
  border-radius: var(--radius);
  background: var(--c-surface);
  color: var(--c-text);
  cursor: pointer;
  /* 禁止裁切底部操作文案 */
  overflow: visible;
  transition: border-color .15s ease, box-shadow .15s ease, background .15s ease;
}
.agent-item:hover { background: var(--c-bg); }
.agent-item.active {
  border-color: var(--c-primary);
  background: var(--c-primary-soft);
  color: var(--c-primary);
  box-shadow: 0 0 0 2px rgba(13, 107, 103, .08);
}
.agent-main {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  min-width: 0;
  flex: 1;
}
.info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.info b {
  display: block;
  font-size: 14px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.info small {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  overflow: hidden;
  text-overflow: ellipsis;
  margin: 0;
  font-size: 12px;
  line-height: 1.45;
  color: var(--c-text-muted);
  word-break: break-word;
}
.caps { display: flex; flex-wrap: wrap; gap: 5px; margin-top: 4px; }
.cap {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 3px;
  background: var(--c-bg);
  color: var(--c-text-muted);
}
.cap.confirm { color: var(--c-warning); background: #fdf2df; }
/** 底部操作行：始终占位，不被描述挤没 */
.choose-state {
  flex: none;
  display: inline-flex;
  align-items: center;
  align-self: flex-start;
  margin-top: auto;
  padding: 4px 0 0;
  color: var(--c-primary);
  font-size: 12px;
  font-weight: 650;
  line-height: 1.2;
}
.choose-state.selected { color: var(--c-primary); }
.agent-item.active .choose-state { color: var(--c-primary); }
@media (max-width: 600px) {
  .picker { grid-template-columns: 1fr; }
}
</style>
