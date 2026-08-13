<script setup lang="ts">
import type { Agent } from '../types'
import type { RunStatusView } from '../types'
import RunStatusBar from './RunStatusBar.vue'

const props = defineProps<{
  open: boolean
  run: RunStatusView | null
  agent: Agent | null
}>()
const emit = defineEmits<{ (e: 'close'): void; (e: 'cancel'): void }>()

function statusLabel(s: string): string {
  return { RUNNING: '正在处理', COMPLETED: '已完成', FAILED: '处理失败', WAITING_INPUT: '等待确认', CANCELLED: '已取消' }[s] || s
}
function elapsed(run: RunStatusView): string {
  if (!run.startedAt) return ''
  const start = new Date(run.startedAt).getTime()
  const end = run.finishedAt ? new Date(run.finishedAt).getTime() : Date.now()
  const sec = Math.round((end - start) / 1000)
  if (sec < 60) return sec + ' 秒'
  return Math.floor(sec / 60) + ' 分 ' + (sec % 60) + ' 秒'
}
</script>

<template>
  <transition name="drawer">
    <aside v-if="open" class="drawer">
      <header class="drawer-head">
        <h2>处理信息</h2>
        <button class="icon-btn" @click="emit('close')" aria-label="关闭处理信息">✕</button>
      </header>
      <div class="drawer-body">
        <div v-if="agent" class="block">
          <span class="label">智能体</span>
          <p class="val">{{ agent.name }}</p>
        </div>
        <div v-if="run" class="block">
          <span class="label">处理状态</span>
          <p class="val">{{ statusLabel(run.status) }}</p>
        </div>
        <div v-if="run" class="block">
          <span class="label">耗时</span>
          <p class="val">{{ elapsed(run) }}</p>
        </div>
        <div v-if="run?.errorMessage" class="block">
          <span class="label">失败原因</span>
          <p class="val err">{{ run.errorMessage }}</p>
        </div>
        <RunStatusBar :run="run" :sending="false" @cancel="emit('cancel')" />
        <div v-if="!run" class="empty-state" style="padding: 40px 12px;">
          <p>暂无处理信息</p>
          <p style="font-size: 12px;">发送任务后，处理状态与耗时将在这里显示。</p>
        </div>
      </div>
    </aside>
  </transition>
  <div v-if="open" class="drawer-mask" @click="emit('close')"></div>
</template>

<style scoped>
.drawer { position: fixed; right: 0; top: 0; bottom: 0; width: 340px; max-width: 88vw; background: var(--c-surface); box-shadow: var(--shadow); z-index: 35; display: flex; flex-direction: column; }
.drawer-head { display: flex; justify-content: space-between; align-items: center; padding: 16px 18px; border-bottom: 1px solid var(--c-border); }
.drawer-head h2 { margin: 0; font-size: 15px; }
.icon-btn { border: 0; background: transparent; cursor: pointer; font-size: 18px; color: var(--c-text-muted); padding: 4px; border-radius: var(--radius); }
.icon-btn:hover { background: var(--c-bg); }
.drawer-body { flex: 1; overflow-y: auto; padding: 8px 0; }
.block { padding: 10px 18px; }
.label { display: block; font-size: 11px; color: var(--c-text-muted); margin-bottom: 3px; }
.val { margin: 0; font-size: 14px; line-height: 1.5; }
.val.err { color: var(--c-danger); }
.drawer-mask { position: fixed; inset: 0; background: rgba(23,33,43,.25); z-index: 34; }
.drawer-enter-active, .drawer-leave-active { transition: transform .22s ease; }
.drawer-enter-from, .drawer-leave-to { transform: translateX(100%); }
</style>
