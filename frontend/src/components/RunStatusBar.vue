<script setup lang="ts">
import { computed } from 'vue'
import type { RunStatusView } from '../types'

const props = defineProps<{ run: RunStatusView | null; sending: boolean }>()
const emit = defineEmits<{ (e: 'cancel'): void }>()

const status = computed(() => props.run?.status || null)

function statusLabel(s: string): string {
  return { RUNNING: '正在处理', COMPLETED: '已完成', FAILED: '处理失败', WAITING_INPUT: '等待你的确认', CANCELLED: '已取消' }[s] || s
}
function statusClass(s: string): string {
  return { RUNNING: 'chip-running', COMPLETED: 'chip-success', FAILED: 'chip-failed', WAITING_INPUT: 'chip-waiting', CANCELLED: 'chip-cancelled' }[s] || ''
}
function elapsed(): string {
  if (!props.run) return ''
  const start = new Date(props.run.startedAt).getTime()
  const end = props.run.finishedAt ? new Date(props.run.finishedAt).getTime() : Date.now()
  const sec = Math.round((end - start) / 1000)
  if (sec < 60) return sec + 's'
  return Math.floor(sec / 60) + 'm ' + (sec % 60) + 's'
}
</script>

<template>
  <div v-if="run" class="run-bar">
    <div class="row">
      <span class="chip" :class="statusClass(status || '')"><i></i>{{ statusLabel(status || '') }}</span>
      <span v-if="status === 'RUNNING' || status === 'WAITING_INPUT'" class="elapsed">{{ elapsed() }}</span>
      <button v-if="status === 'RUNNING'" class="btn btn-cancel" @click="emit('cancel')">停止</button>
    </div>
    <p v-if="run.errorMessage" class="error-text">{{ run.errorMessage }}</p>
  </div>
</template>

<style scoped>
.run-bar { padding: 12px 16px; border-bottom: 1px solid var(--c-border-soft); background: var(--c-surface); }
.row { display: flex; align-items: center; gap: 12px; }
.elapsed { font-size: 12px; color: var(--c-text-muted); font-variant-numeric: tabular-nums; }
.btn-cancel { margin-left: auto; padding: 4px 12px; font-size: 12px; border-color: var(--c-danger); color: var(--c-danger); background: var(--c-surface); }
.btn-cancel:hover { background: var(--c-danger-soft); }
.error-text { margin: 8px 0 0; font-size: 12px; color: var(--c-danger); }
</style>
