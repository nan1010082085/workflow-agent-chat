<script setup lang="ts">
import { computed } from 'vue'
import type { Message, RunStatusView, WaitingPayload } from '../types'
import MessageParts from './message/MessageParts.vue'
import DocumentSummaryList from './message/DocumentSummaryList.vue'
import MessageAttachmentList from './message/MessageAttachmentList.vue'

const props = defineProps<{
  message: Message
  run: RunStatusView | null
  sending: boolean
}>()
const emit = defineEmits<{
  (e: 'resume', action: string, payload?: string): void
  (e: 'cancel'): void
  (e: 'retry'): void
  (e: 'open-process'): void
}>()

const isUser = computed(() => props.message.role === 'user')
const isAssistant = computed(() => props.message.role === 'assistant')

/** 轻量处理状态文案（F-05） */
function statusLabel(status: string): string {
  return {
    PENDING: '排队中', RUNNING: '正在处理', WAITING_INPUT: '等待你的确认',
    COMPLETED: '已完成', FAILED: '处理失败', CANCELLED: '已取消',
  }[status] || status
}

function statusClass(status: string): string {
  return {
    RUNNING: 'chip-running', WAITING_INPUT: 'chip-waiting',
    COMPLETED: 'chip-success', FAILED: 'chip-failed', CANCELLED: 'chip-cancelled',
  }[status] || 'chip-cancelled'
}

/** inline approval：waiting 卡片紧邻助手消息（F-04） */
const waiting = computed<WaitingPayload | null>(() => {
  if (props.message.status !== 'WAITING_INPUT') return null
  return props.run?.waiting || null
})

const hasProcessMeta = computed(() =>
  Boolean(props.message.thinking)
    || Boolean(props.message.toolCalls?.length)
    || Boolean(props.message.workflowExecution),
)

let inputValue = ''
function onInput(e: Event) { inputValue = (e.target as HTMLTextAreaElement).value }
function submit(action: string) {
  emit('resume', action, inputValue || undefined)
  inputValue = ''
}

const showTyping = computed(() =>
  isAssistant.value && props.message.status === 'RUNNING' && !props.message.content,
)

const showContent = computed(() =>
  Boolean(props.message.content)
    || Boolean(props.message.documentSummaries?.length)
    || Boolean(props.message.attachments?.length),
)

const showWaitingHint = computed(() =>
  isAssistant.value && props.message.status === 'WAITING_INPUT' && !props.message.content,
)

const showFailedHint = computed(() =>
  isAssistant.value && props.message.status === 'FAILED' && !props.message.content,
)

const showCancelledHint = computed(() =>
  isAssistant.value && props.message.status === 'CANCELLED' && !props.message.content,
)

function copyContent() {
  if (props.message.content) navigator.clipboard?.writeText(props.message.content)
}

/**
 * 下载整段助手正文。
 */
function downloadContent() {
  if (!props.message.content) return
  const blob = new Blob([props.message.content], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = 'reply.md'
  a.click()
  URL.revokeObjectURL(url)
}

function toolStatus(tool: NonNullable<Message['toolCalls']>[number]): string {
  if (tool.error) return '失败'
  if (tool.result !== undefined) return '已完成'
  return '处理中'
}
</script>

<template>
  <div class="message" :class="message.role">
    <span v-if="isAssistant" class="avatar" aria-hidden="true">
      <svg viewBox="0 0 16 16" width="14" height="14">
        <path
          d="M8 1.8 9.6 5.4l3.8.4-2.9 2.6.9 3.7L8 10.4l-3.4 1.7.9-3.7L2.6 5.8l3.8-.4L8 1.8Z"
          fill="currentColor"
        />
      </svg>
    </span>
    <div class="bubble-wrap">
      <p v-if="isAssistant && message.tip" class="tip">{{ message.tip }}</p>

      <div v-if="showTyping" class="typing">
        <span class="status-text">正在处理</span>
        <span class="dots"><i></i><i></i><i></i></span>
      </div>

      <div v-else-if="showContent" class="result-wrap">
        <div class="bubble message-content" :class="{ user: isUser }">
          <MessageAttachmentList
            v-if="message.attachments?.length"
            :attachments="message.attachments"
          />
          <MessageParts v-if="message.content" :content="message.content" />
          <DocumentSummaryList
            v-if="message.documentSummaries?.length"
            :summaries="message.documentSummaries"
          />
        </div>
        <div v-if="isAssistant && message.status === 'COMPLETED'" class="result-actions">
          <el-tooltip content="复制全文" placement="top" :show-after="200">
            <button class="icon-btn" type="button" aria-label="复制" @click="copyContent">
              <svg viewBox="0 0 16 16" width="14" height="14" aria-hidden="true">
                <rect x="5.5" y="5.5" width="8" height="8" rx="1.5" fill="none" stroke="currentColor" stroke-width="1.4" />
                <path d="M3.5 10.5V3.5h7" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" />
              </svg>
            </button>
          </el-tooltip>
          <el-tooltip content="下载" placement="top" :show-after="200">
            <button class="icon-btn" type="button" aria-label="下载" @click="downloadContent">
              <svg viewBox="0 0 16 16" width="14" height="14" aria-hidden="true">
                <path d="M8 2.5v7.2M5.2 7.5 8 10.3l2.8-2.8M3.5 13h9" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round" />
              </svg>
            </button>
          </el-tooltip>
          <el-tooltip v-if="hasProcessMeta" content="处理信息" placement="top" :show-after="200">
            <button class="text-link" type="button" @click="emit('open-process')">处理信息</button>
          </el-tooltip>
        </div>
        <div v-if="isAssistant && message.status === 'FAILED'" class="fail-actions">
          <el-tooltip content="重试" placement="top" :show-after="200">
            <button class="icon-btn" type="button" aria-label="重试" @click="emit('retry')">
              <svg viewBox="0 0 16 16" width="14" height="14" aria-hidden="true">
                <path d="M3.2 8a4.8 4.8 0 0 1 8.3-3.2" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" />
                <path d="M12.8 8a4.8 4.8 0 0 1-8.3 3.2" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" />
                <path d="M11.2 2.6v2.6h-2.6M4.8 13.4v-2.6h2.6" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round" />
              </svg>
            </button>
          </el-tooltip>
          <el-tooltip content="换一个智能体" placement="top" :show-after="200">
            <button class="icon-btn" type="button" aria-label="换一个智能体" @click="emit('cancel')">
              <svg viewBox="0 0 16 16" width="14" height="14" aria-hidden="true">
                <circle cx="5.2" cy="5" r="1.8" fill="none" stroke="currentColor" stroke-width="1.4" />
                <circle cx="10.8" cy="5" r="1.8" fill="none" stroke="currentColor" stroke-width="1.4" />
                <path d="M2.4 12.4c.4-1.8 1.6-2.8 2.8-2.8h.6c.7 0 1.3.3 1.8.8M13.6 12.4c-.4-1.8-1.6-2.8-2.8-2.8h-.6c-.7 0-1.3.3-1.8.8" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" />
              </svg>
            </button>
          </el-tooltip>
        </div>
      </div>

      <div v-else-if="showWaitingHint" class="bubble waiting-hint">
        智能体正在等待你的确认，请在下方操作。
      </div>
      <div v-else-if="showFailedHint" class="bubble failed-hint">
        处理失败
        <div class="fail-actions">
          <el-tooltip content="重试" placement="top" :show-after="200">
            <button class="icon-btn" type="button" aria-label="重试" @click="emit('retry')">
              <svg viewBox="0 0 16 16" width="14" height="14" aria-hidden="true">
                <path d="M3.2 8a4.8 4.8 0 0 1 8.3-3.2" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" />
                <path d="M12.8 8a4.8 4.8 0 0 1-8.3 3.2" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" />
                <path d="M11.2 2.6v2.6h-2.6M4.8 13.4v-2.6h2.6" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round" />
              </svg>
            </button>
          </el-tooltip>
          <el-tooltip content="换一个智能体" placement="top" :show-after="200">
            <button class="icon-btn" type="button" aria-label="换一个智能体" @click="emit('cancel')">
              <svg viewBox="0 0 16 16" width="14" height="14" aria-hidden="true">
                <circle cx="5.2" cy="5" r="1.8" fill="none" stroke="currentColor" stroke-width="1.4" />
                <circle cx="10.8" cy="5" r="1.8" fill="none" stroke="currentColor" stroke-width="1.4" />
                <path d="M2.4 12.4c.4-1.8 1.6-2.8 2.8-2.8h.6c.7 0 1.3.3 1.8.8M13.6 12.4c-.4-1.8-1.6-2.8-2.8-2.8h-.6c-.7 0-1.3.3-1.8.8" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" />
              </svg>
            </button>
          </el-tooltip>
        </div>
      </div>
      <div v-else-if="showCancelledHint" class="bubble cancelled-hint">已取消</div>

      <!-- 过程信息默认折叠，避免淹没结果 -->
      <details v-if="isAssistant && message.thinking" class="detail-block">
        <summary>思考过程</summary>
        <div class="detail-content">{{ message.thinking }}</div>
      </details>
      <details v-if="isAssistant && message.toolCalls?.length" class="detail-block">
        <summary>处理步骤（{{ message.toolCalls.length }}）</summary>
        <div v-for="tool in message.toolCalls" :key="tool.id || tool.name" class="tool-row">
          <strong>{{ tool.name }}</strong>
          <span :class="{ err: Boolean(tool.error) }">{{ toolStatus(tool) }}</span>
        </div>
      </details>
      <details v-if="isAssistant && message.workflowExecution" class="detail-block">
        <summary>处理摘要 · {{ message.workflowExecution.status }}</summary>
        <div class="detail-content">
          <div>{{ message.workflowExecution.workflowName || '任务处理' }}</div>
          <div v-if="message.workflowExecution.durationMs != null">
            耗时 {{ Math.round(message.workflowExecution.durationMs / 1000) }} 秒
          </div>
          <div v-if="message.workflowExecution.error" class="err">{{ message.workflowExecution.error }}</div>
        </div>
      </details>

      <span
        v-if="isAssistant && message.status !== 'COMPLETED' && message.status !== 'CANCELLED'"
        class="chip"
        :class="statusClass(message.status)"
      >
        <i></i>{{ statusLabel(message.status) }}
      </span>
      <span v-else-if="isAssistant && message.status === 'COMPLETED'" class="chip chip-success">
        <i></i>已完成
      </span>

      <div v-if="waiting" class="inline-approval" :class="{ dangerous: waiting.dangerous }">
        <div class="approval-head">
          <span class="title">需要你的确认</span>
          <span v-if="waiting.dangerous" class="danger-tag">需谨慎</span>
        </div>
        <p class="prompt">{{ waiting.prompt }}</p>
        <div v-for="f in waiting.fields" :key="f.key" class="field">
          <label>{{ f.label }}</label>
          <textarea
            v-if="f.type === 'textarea'"
            rows="2"
            :placeholder="'请输入' + f.label"
            @input="onInput"
          />
          <input v-else :placeholder="'请输入' + f.label" @input="onInput" />
        </div>
        <div class="actions">
          <button
            v-for="a in waiting.actions"
            :key="a.action"
            type="button"
            class="btn"
            :class="a.style === 'danger' ? 'btn-danger' : 'btn-primary'"
            :disabled="sending"
            @click="submit(a.action)"
          >
            {{ a.label }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.message { display: flex; gap: 12px; max-width: 900px; margin: 0 auto 22px; align-items: flex-start; }
.message.user { justify-content: flex-end; }
.avatar {
  flex: none;
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  margin-top: 2px;
  color: #fff;
  background: var(--c-accent);
  border-radius: 8px;
}
.bubble-wrap { display: flex; flex-direction: column; gap: 8px; max-width: 760px; min-width: 0; }
.message.user .bubble-wrap { align-items: flex-end; max-width: 640px; }
.result-wrap { display: flex; flex-direction: column; align-items: stretch; width: fit-content; max-width: 100%; }
.message.user .result-wrap { align-items: flex-end; }
.tip {
  margin: 0;
  padding: 0 2px;
  color: var(--c-text-muted);
  font-size: 12px;
}
.bubble {
  margin: 0;
  padding: 12px 16px;
  width: fit-content;
  max-width: 100%;
  line-height: 1.7;
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: 14px;
  word-break: break-word;
  box-shadow: 0 1px 2px rgba(20, 40, 40, .04);
}
.bubble.user {
  background: #e8f3f2;
  border-color: #cfe3e1;
}
.message-content > :first-child { margin-top: 0; }
.message-content > :last-child { margin-bottom: 0; }
.detail-block {
  max-width: 100%;
  color: var(--c-text-muted);
  font-size: 12px;
  border: 1px solid var(--c-border-soft, var(--c-border));
  border-radius: 10px;
  background: rgba(255,255,255,.55);
  padding: 2px 10px 8px;
}
.detail-block summary {
  cursor: pointer;
  padding: 6px 0;
  list-style: none;
  font-weight: 600;
}
.detail-block summary::-webkit-details-marker { display: none; }
.detail-block summary::before {
  content: '▸';
  display: inline-block;
  margin-right: 6px;
  transition: transform .15s ease;
}
.detail-block[open] summary::before { transform: rotate(90deg); }
.detail-content {
  margin-top: 2px;
  padding: 8px 10px;
  background: var(--c-bg);
  border-radius: 8px;
  white-space: pre-wrap;
  line-height: 1.55;
  color: var(--c-text-secondary);
}
.tool-row { display: flex; justify-content: space-between; gap: 16px; margin-top: 6px; }
.err { color: var(--c-danger); }
.waiting-hint { color: var(--c-warning); background: #fdf2df; border-color: #f0d9a8; }
.failed-hint { color: var(--c-danger); background: var(--c-danger-soft); border-color: #f0c4be; }
.cancelled-hint { color: var(--c-text-muted); background: #eef2f2; border-color: var(--c-border); }
.typing {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 15px;
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: 14px;
}
.status-text { font-size: 13px; color: var(--c-text-muted); }
.dots { display: inline-flex; gap: 4px; }
.dots i { width: 6px; height: 6px; background: var(--c-running); border-radius: 50%; animation: blink 1.4s infinite both; }
.dots i:nth-child(2) { animation-delay: .2s; }
.dots i:nth-child(3) { animation-delay: .4s; }
@keyframes blink { 0%, 80%, 100% { opacity: .3; } 40% { opacity: 1; } }
.result-actions, .fail-actions { display: flex; align-items: center; gap: 4px; margin-top: 4px; }
.text-link {
  border: 0;
  background: transparent;
  color: var(--c-text-muted);
  font-size: 12px;
  cursor: pointer;
  padding: 4px 6px;
  border-radius: 6px;
}
.text-link:hover { color: var(--c-primary); background: var(--c-primary-soft); }
.icon-btn {
  display: inline-grid;
  place-items: center;
  width: 28px;
  height: 28px;
  padding: 0;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--c-text-muted);
  cursor: pointer;
}
.icon-btn:hover { color: var(--c-primary); background: var(--c-primary-soft); }
.inline-approval {
  margin-top: 4px;
  padding: 14px;
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-left: 3px solid var(--c-primary);
  border-radius: var(--radius);
}
.inline-approval.dangerous { border-left-color: var(--c-danger); background: var(--c-danger-soft); }
.approval-head { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.title { font-weight: 700; font-size: 13px; }
.danger-tag { font-size: 10px; font-weight: 700; color: #fff; background: var(--c-danger); padding: 1px 6px; border-radius: 3px; }
.prompt { margin: 0 0 10px; font-size: 13px; line-height: 1.55; color: var(--c-text-secondary); }
.field { margin-bottom: 8px; }
.field label { display: block; font-size: 11px; color: var(--c-text-muted); margin-bottom: 3px; }
.field input, .field textarea {
  display: block;
  width: 100%;
  padding: 7px 10px;
  border: 1px solid var(--c-border);
  border-radius: var(--radius);
  font-size: 13px;
  outline: none;
  background: var(--c-surface);
}
.actions { display: flex; gap: 8px; justify-content: flex-end; }
.chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  width: fit-content;
  padding: 3px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  background: #eef2f2;
  color: var(--c-text-secondary);
}
.chip i { width: 6px; height: 6px; border-radius: 50%; background: currentColor; }
.chip-running { color: var(--c-running); background: #e8f4ff; }
.chip-waiting { color: var(--c-warning); background: #fdf2df; }
.chip-success { color: var(--c-primary); background: var(--c-primary-soft); }
.chip-failed { color: var(--c-danger); background: var(--c-danger-soft); }
.chip-cancelled { color: var(--c-text-muted); }
</style>
