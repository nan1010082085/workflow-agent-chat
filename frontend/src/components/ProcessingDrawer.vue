<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import type { Agent, Message, RunStatusView } from '../types'
import RunStatusBar from './RunStatusBar.vue'

const props = defineProps<{
  open: boolean
  run: RunStatusView | null
  agent: Agent | null
  /** 当前查看的助手消息（思考/步骤/工作流节点） */
  message?: Message | null
  /** 模型对话时的模型名 */
  modelName?: string | null
  mode?: 'model' | 'agent'
}>()
const emit = defineEmits<{ (e: 'close'): void; (e: 'cancel'): void }>()

const nowTick = ref(Date.now())
let tickTimer: ReturnType<typeof setInterval> | null = null

/**
 * 运行中时刷新耗时显示。
 */
watch(
  () => [props.open, props.run?.status] as const,
  ([open, status]) => {
    if (tickTimer) {
      clearInterval(tickTimer)
      tickTimer = null
    }
    if (open && (status === 'RUNNING' || status === 'WAITING_INPUT')) {
      tickTimer = setInterval(() => { nowTick.value = Date.now() }, 1000)
    }
  },
  { immediate: true },
)

onUnmounted(() => {
  if (tickTimer) clearInterval(tickTimer)
})

function statusLabel(s: string): string {
  return {
    PENDING: '排队中',
    RUNNING: '正在处理',
    COMPLETED: '已完成',
    FAILED: '处理失败',
    WAITING_INPUT: '等待确认',
    CANCELLED: '已取消',
  }[s] || s
}

/**
 * 格式化耗时。
 */
function formatElapsed(startedAt?: string | null, finishedAt?: string | null): string {
  if (!startedAt) return '—'
  const start = new Date(startedAt).getTime()
  const end = finishedAt ? new Date(finishedAt).getTime() : nowTick.value
  if (Number.isNaN(start)) return '—'
  const sec = Math.max(0, Math.round((end - start) / 1000))
  if (sec < 60) return `${sec} 秒`
  return `${Math.floor(sec / 60)} 分 ${sec % 60} 秒`
}

function formatDurationMs(ms?: number): string {
  if (ms == null || Number.isNaN(ms)) return '—'
  if (ms < 1000) return `${ms} ms`
  const sec = Math.round(ms / 1000)
  if (sec < 60) return `${sec} 秒`
  return `${Math.floor(sec / 60)} 分 ${sec % 60} 秒`
}

function toolStatus(tool: NonNullable<Message['toolCalls']>[number]): string {
  if (tool.error) return '失败'
  if (tool.result !== undefined) return '完成'
  return '已调用'
}

const messageStatus = computed(() => props.message?.status)
const thinking = computed(() => props.message?.thinking?.trim() || '')
const tools = computed(() => props.message?.toolCalls || [])
const workflow = computed(() => props.message?.workflowExecution || null)
const nodes = computed(() => workflow.value?.nodeRecords || [])

const hasContent = computed(() =>
  Boolean(props.agent)
    || Boolean(props.modelName)
    || Boolean(props.run)
    || Boolean(props.message)
    || Boolean(thinking.value)
    || tools.value.length > 0
    || Boolean(workflow.value),
)

const primaryStatus = computed(() => {
  if (props.run?.status) return statusLabel(props.run.status)
  if (messageStatus.value) return statusLabel(messageStatus.value)
  if (workflow.value?.status) return statusLabel(workflow.value.status)
  return ''
})

const primaryElapsed = computed(() => {
  if (props.run) return formatElapsed(props.run.startedAt, props.run.finishedAt)
  if (workflow.value?.durationMs != null) return formatDurationMs(workflow.value.durationMs)
  return ''
})

const errorText = computed(() =>
  props.run?.errorMessage
    || workflow.value?.error
    || (props.message?.status === 'FAILED' ? '本轮处理失败' : '')
    || '',
)
</script>

<template>
  <transition name="drawer">
    <aside v-if="open" class="drawer">
      <header class="drawer-head">
        <h2>处理信息</h2>
        <button class="icon-btn" type="button" aria-label="关闭处理信息" @click="emit('close')">✕</button>
      </header>
      <div class="drawer-body">
        <template v-if="hasContent">
          <div class="block">
            <span class="label">对话模式</span>
            <p class="val">{{ mode === 'agent' ? '智能体任务' : '模型对话' }}</p>
          </div>

          <div v-if="agent" class="block">
            <span class="label">智能体</span>
            <p class="val">{{ agent.name }}</p>
          </div>
          <div v-else-if="modelName" class="block">
            <span class="label">模型</span>
            <p class="val">{{ modelName }}</p>
          </div>

          <div v-if="primaryStatus" class="block">
            <span class="label">处理状态</span>
            <p class="val">{{ primaryStatus }}</p>
          </div>
          <div v-if="primaryElapsed" class="block">
            <span class="label">耗时</span>
            <p class="val mono">{{ primaryElapsed }}</p>
          </div>
          <div v-if="run?.runtimeExecutionId || workflow?.executionId" class="block">
            <span class="label">执行 ID</span>
            <p class="val mono small">{{ run?.runtimeExecutionId || workflow?.executionId }}</p>
          </div>
          <div v-if="errorText" class="block">
            <span class="label">失败原因</span>
            <p class="val err">{{ errorText }}</p>
          </div>

          <RunStatusBar
            v-if="run && (run.status === 'RUNNING' || run.status === 'WAITING_INPUT')"
            :run="run"
            :sending="false"
            @cancel="emit('cancel')"
          />

          <section v-if="thinking" class="section">
            <h3>思考过程</h3>
            <pre class="panel">{{ thinking }}</pre>
          </section>

          <section v-if="tools.length" class="section">
            <h3>处理步骤（{{ tools.length }}）</h3>
            <ul class="step-list">
              <li v-for="(tool, i) in tools" :key="tool.id || `${tool.name}-${i}`">
                <div class="step-head">
                  <strong>{{ tool.name }}</strong>
                  <span class="chip" :class="{ err: Boolean(tool.error) }">{{ toolStatus(tool) }}</span>
                </div>
                <pre v-if="tool.error" class="panel err-panel">{{ tool.error }}</pre>
                <pre
                  v-else-if="tool.result !== undefined"
                  class="panel"
                >{{ typeof tool.result === 'string' ? tool.result : JSON.stringify(tool.result, null, 2) }}</pre>
              </li>
            </ul>
          </section>

          <section v-if="workflow" class="section">
            <h3>工作流</h3>
            <p class="val">{{ workflow.workflowName || workflow.workflowId }}</p>
            <p v-if="workflow.durationMs != null" class="meta">总耗时 {{ formatDurationMs(workflow.durationMs) }}</p>
            <ul v-if="nodes.length" class="node-list">
              <li v-for="n in nodes" :key="n.nodeId">
                <div class="step-head">
                  <strong>{{ n.nodeName || n.nodeId }}</strong>
                  <span class="chip">{{ statusLabel(n.status) }}</span>
                </div>
                <p class="meta">
                  {{ n.nodeType }}
                  <template v-if="n.durationMs != null"> · {{ formatDurationMs(n.durationMs) }}</template>
                </p>
              </li>
            </ul>
          </section>

          <p
            v-if="!thinking && !tools.length && !workflow && !run"
            class="hint"
          >
            本轮暂无思考过程或步骤明细；状态类信息已列在上方。
          </p>
        </template>

        <div v-else class="empty-state">
          <p>暂无处理信息</p>
          <p class="hint">发送消息后，这里会显示状态、耗时、思考过程与处理步骤。</p>
        </div>
      </div>
    </aside>
  </transition>
  <div v-if="open" class="drawer-mask" @click="emit('close')" />
</template>

<style scoped>
.drawer {
  position: fixed;
  right: 0;
  top: 0;
  bottom: 0;
  width: 380px;
  max-width: 92vw;
  background: var(--c-surface);
  box-shadow: var(--shadow);
  z-index: 35;
  display: flex;
  flex-direction: column;
}
.drawer-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 18px;
  border-bottom: 1px solid var(--c-border);
}
.drawer-head h2 { margin: 0; font-size: 15px; }
.icon-btn {
  border: 0;
  background: transparent;
  cursor: pointer;
  font-size: 18px;
  color: var(--c-text-muted);
  padding: 4px;
  border-radius: var(--radius);
}
.icon-btn:hover { background: var(--c-bg); }
.drawer-body { flex: 1; overflow-y: auto; padding: 8px 0 20px; }
.block { padding: 10px 18px; }
.label { display: block; font-size: 11px; color: var(--c-text-muted); margin-bottom: 3px; }
.val { margin: 0; font-size: 14px; line-height: 1.5; word-break: break-word; }
.val.err { color: var(--c-danger); }
.val.mono, .mono { font-variant-numeric: tabular-nums; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; }
.val.small { font-size: 12px; color: var(--c-text-secondary); }
.section { padding: 14px 18px 8px; border-top: 1px solid var(--c-border-soft); }
.section h3 { margin: 0 0 10px; font-size: 13px; font-weight: 650; color: var(--c-text); }
.panel {
  margin: 0;
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--c-bg);
  border: 1px solid var(--c-border-soft);
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 12.5px;
  line-height: 1.55;
  color: var(--c-text-secondary);
  max-height: 280px;
  overflow: auto;
}
.err-panel { color: var(--c-danger); border-color: #f0c4be; background: #fff8f7; }
.step-list, .node-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 10px; }
.step-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-bottom: 4px; }
.step-head strong { font-size: 13px; font-weight: 650; }
.chip {
  flex: none;
  font-size: 11px;
  padding: 1px 7px;
  border-radius: 999px;
  background: #eef2f2;
  color: var(--c-text-muted);
}
.chip.err { background: var(--c-danger-soft); color: var(--c-danger); }
.meta { margin: 2px 0 0; font-size: 12px; color: var(--c-text-muted); }
.hint { margin: 8px 18px 0; font-size: 12px; color: var(--c-text-muted); line-height: 1.5; }
.empty-state { padding: 48px 20px; text-align: center; color: var(--c-text-muted); }
.empty-state p { margin: 0 0 8px; }
.drawer-mask { position: fixed; inset: 0; background: rgba(23,33,43,.25); z-index: 34; }
.drawer-enter-active, .drawer-leave-active { transition: transform .22s ease; }
.drawer-enter-from, .drawer-leave-to { transform: translateX(100%); }
:deep(.run-bar) { border-top: 1px solid var(--c-border-soft); border-bottom: 1px solid var(--c-border-soft); }
</style>
