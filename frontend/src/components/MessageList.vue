<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import type { Message, RunStatusView } from '../types'
import MessageBubble from './MessageBubble.vue'

const props = defineProps<{
  messages: Message[]
  loading: boolean
  currentRun: RunStatusView | null
  sending?: boolean
}>()
const emit = defineEmits<{
  (e: 'resume', action: string, payload?: string): void
  (e: 'cancel'): void
  (e: 'retry'): void
  (e: 'open-process', message: Message): void
}>()
const container = ref<HTMLElement | null>(null)

const hasContent = computed(() => props.messages.length > 0)

watch(() => props.messages.length, async () => {
  await nextTick()
  if (container.value) container.value.scrollTop = container.value.scrollHeight
}, { flush: 'post' })

/** 正文/状态变化时也滚到底，避免确认态内容回填后看不见 */
watch(
  () => props.messages.map((m) => `${m.id}:${m.status}:${(m.content || '').length}`).join('|'),
  async () => {
    await nextTick()
    if (container.value) container.value.scrollTop = container.value.scrollHeight
  },
  { flush: 'post' },
)

// 把当前 run 关联到对应消息（用于 inline approval）
function runForMessage(m: Message): RunStatusView | null {
  if (!props.currentRun || !m.runtimeExecutionId) return null
  return props.currentRun.runtimeExecutionId === m.runtimeExecutionId ? props.currentRun : null
}
</script>

<template>
  <div ref="container" class="message-list">
    <div v-if="loading && !messages.length" class="skeleton-line">
      <div class="skeleton" style="height: 14px; width: 60%;"></div>
      <div class="skeleton" style="height: 14px; width: 45%; margin-top: 8px;"></div>
    </div>
    <template v-for="m in messages" :key="m.id">
      <MessageBubble
        :message="m"
        :run="runForMessage(m)"
        :sending="Boolean(sending)"
        :session-messages="messages"
        @resume="(a, p) => emit('resume', a, p)"
        @cancel="emit('cancel')"
        @retry="emit('retry')"
        @open-process="emit('open-process', m)"
      />
    </template>
  </div>
</template>

<style scoped>
.message-list { flex: 1; overflow-y: auto; padding: 24px 32px; background: transparent; }
.skeleton-line { max-width: 960px; margin: 0 auto; padding: 16px; background: var(--c-surface); border: 1px solid var(--c-border-soft); border-radius: var(--radius); }
@media (max-width: 767px) { .message-list { padding: 16px; } }
</style>
