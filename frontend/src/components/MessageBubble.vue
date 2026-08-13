<script setup lang="ts">
import { computed } from 'vue'
import type { Message, RunStatusView, WaitingPayload } from '../types'
import { splitTextAndCodeBlocks } from '../utils/textParser'

const props = defineProps<{
  message: Message
  run: RunStatusView | null
  sending: boolean
}>()
const emit = defineEmits<{
  (e: 'resume', action: string, payload?: string): void
  (e: 'cancel'): void
  (e: 'retry'): void
}>()

const isUser = computed(() => props.message.role === 'user')
const isAssistant = computed(() => props.message.role === 'assistant')

// 轻量处理状态文案（F-05）
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

// inline approval：waiting 卡片紧邻助手消息（F-04）
const waiting = computed<WaitingPayload | null>(() => {
  if (props.message.status !== 'WAITING_INPUT') return null
  return props.run?.waiting || null
})

const approvalValue = computed({
  get: () => '',
  set: () => {},
})
let inputValue = ''
function onInput(e: Event) { inputValue = (e.target as HTMLTextAreaElement).value }
function submit(action: string) {
  emit('resume', action, inputValue || undefined)
  inputValue = ''
}

const contentParts = computed(() => splitTextAndCodeBlocks(props.message.content || ''))

// 简易 Markdown 渲染（F-06）：处理 **粗体**、`code`、换行、列表
function renderContent(content: string): string {
  if (!content) return ''
  let html = content
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
  html = html.replace(/```([\s\S]*?)```/g, '<pre><code>$1</code></pre>')
  html = html.replace(/`([^`]+)`/g, '<code>$1</code>')
  html = html.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
  html = html.replace(/^[-*] (.+)$/gm, '<li>$1</li>')
  html = html.replace(/(<li>[\s\S]*?<\/li>)/g, '<ul>$1</ul>')
  html = html.replace(/\n/g, '<br>')
  // 清理 ul 间多余的 br
  html = html.replace(/<br>(<ul>)/g, '$1').replace(/(<\/ul>)<br>/g, '$1')
  return html
}

function copyContent() {
  if (props.message.content) navigator.clipboard?.writeText(props.message.content)
}
</script>

<template>
  <div class="message" :class="message.role">
    <span class="avatar">{{ isUser ? '你' : (props.run?.agentId ? 'AI' : 'AI') }}</span>
    <div class="bubble-wrap">
      <!-- 处理中：轻量指示 -->
      <div v-if="isAssistant && message.status === 'RUNNING' && !message.content" class="typing">
        <span class="status-text">正在处理</span>
        <span class="dots"><i></i><i></i><i></i></span>
      </div>

      <!-- 助手结果 -->
      <div v-else-if="message.content" class="result-wrap">
        <div class="bubble message-content">
          <template v-for="(part, index) in contentParts" :key="index">
            <div v-if="part.type === 'text'" v-html="renderContent(part.content)"></div>
            <pre v-else class="code-block"><code>{{ part.content }}</code></pre>
          </template>
        </div>
        <div v-if="isAssistant && message.status === 'COMPLETED'" class="result-actions">
          <button class="link-btn" @click="copyContent">复制</button>
        </div>
      </div>

      <details v-if="isAssistant && message.thinking" class="detail-block">
        <summary>思考过程</summary>
        <div class="detail-content">{{ message.thinking }}</div>
      </details>
      <details v-if="isAssistant && message.toolCalls?.length" class="detail-block">
        <summary>处理步骤（{{ message.toolCalls.length }}）</summary>
        <div v-for="tool in message.toolCalls" :key="tool.id || tool.name" class="tool-row">
          <strong>{{ tool.name }}</strong><span>{{ tool.error ? '失败' : tool.result !== undefined ? '已完成' : '处理中' }}</span>
        </div>
      </details>

      <!-- 等待确认提示 -->
      <div v-else-if="isAssistant && message.status === 'WAITING_INPUT'" class="bubble waiting-hint">
        智能体正在等待你的确认，请在下方操作。
      </div>

      <!-- 失败 + 下一步动作（F-06） -->
      <div v-else-if="isAssistant && message.status === 'FAILED'" class="bubble failed-hint">
        处理失败{{ message.content ? '：' + message.content : '' }}
        <div class="fail-actions">
          <button class="link-btn" @click="emit('retry')">重试</button>
          <span class="sep">·</span>
          <button class="link-btn" @click="emit('cancel')">换一个智能体</button>
        </div>
      </div>

      <!-- 已取消 -->
      <div v-else-if="isAssistant && message.status === 'CANCELLED' && !message.content" class="bubble cancelled-hint">
        已取消
      </div>

      <!-- 状态标记 -->
      <span v-if="isAssistant && message.status !== 'COMPLETED' && message.status !== 'CANCELLED'"
        class="chip" :class="statusClass(message.status)">
        <i></i>{{ statusLabel(message.status) }}
      </span>
      <span v-else-if="isAssistant && message.status === 'COMPLETED'" class="chip chip-success">
        <i></i>已完成
      </span>

      <!-- inline approval 紧邻助手消息（F-04） -->
      <div v-if="waiting" class="inline-approval" :class="{ dangerous: waiting.dangerous }">
        <div class="approval-head">
          <span class="title">需要你的确认</span>
          <span v-if="waiting.dangerous" class="danger-tag">需谨慎</span>
        </div>
        <p class="prompt">{{ waiting.prompt }}</p>
        <div v-for="f in waiting.fields" :key="f.key" class="field">
          <label>{{ f.label }}</label>
          <textarea v-if="f.type === 'textarea'" @input="onInput" :placeholder="'请输入' + f.label" rows="2"></textarea>
          <input v-else @input="onInput" :placeholder="'请输入' + f.label" />
        </div>
        <div class="actions">
          <button v-for="a in waiting.actions" :key="a.action"
            class="btn" :class="a.style === 'danger' ? 'btn-danger' : 'btn-primary'"
            :disabled="sending"
            @click="submit(a.action)">{{ a.label }}</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.message { display: flex; gap: 12px; max-width: 760px; margin: 0 auto 20px; align-items: flex-start; }
.message.user { justify-content: flex-end; }
.message.user .avatar { order: 2; background: var(--c-primary); }
.avatar { flex: none; display: grid; place-items: center; width: 30px; height: 30px; color: #fff; background: var(--c-accent); border-radius: var(--radius); font-size: 11px; font-weight: 700; }
.bubble-wrap { display: flex; flex-direction: column; gap: 6px; max-width: 640px; min-width: 0; }
.message.user .bubble-wrap { align-items: flex-end; }
.bubble { margin: 0; padding: 12px 15px; line-height: 1.65; background: var(--c-surface); border: 1px solid var(--c-border); border-radius: var(--radius); word-break: break-word; }
.bubble :deep(code) { background: #eef2f2; padding: 1px 5px; border-radius: 3px; font-size: 13px; font-family: ui-monospace, monospace; }
.bubble :deep(pre) { background: #1e2a33; color: #e8efef; padding: 12px; border-radius: var(--radius); overflow-x: auto; margin: 8px 0; }
.bubble :deep(pre code) { background: transparent; color: inherit; padding: 0; }
.bubble :deep(ul) { margin: 6px 0; padding-left: 20px; }
.message-content > :first-child { margin-top: 0; }
.message-content > :last-child { margin-bottom: 0; }
.code-block { margin: 10px 0 0; padding: 12px; overflow-x: auto; border-radius: var(--radius); background: #1e2a33; color: #e8efef; white-space: pre; }
.detail-block { color: var(--c-text-muted); font-size: 12px; }
.detail-block summary { cursor: pointer; }
.detail-content { margin-top: 6px; padding: 8px 10px; background: var(--c-bg); white-space: pre-wrap; line-height: 1.5; }
.tool-row { display: flex; justify-content: space-between; gap: 16px; margin-top: 6px; }
.waiting-hint { color: var(--c-warning); background: #fdf2df; border-color: #f0d9a8; }
.failed-hint { color: var(--c-danger); background: var(--c-danger-soft); border-color: #f0c4be; }
.cancelled-hint { color: var(--c-text-muted); background: #eef2f2; border-color: var(--c-border); }
.typing { display: flex; align-items: center; gap: 10px; padding: 12px 15px; background: var(--c-surface); border: 1px solid var(--c-border); border-radius: var(--radius); }
.status-text { font-size: 13px; color: var(--c-text-muted); }
.dots { display: inline-flex; gap: 4px; }
.dots i { width: 6px; height: 6px; background: var(--c-running); border-radius: 50%; animation: blink 1.4s infinite both; }
.dots i:nth-child(2) { animation-delay: .2s; }
.dots i:nth-child(3) { animation-delay: .4s; }
@keyframes blink { 0%, 80%, 100% { opacity: .3; } 40% { opacity: 1; } }
.result-actions, .fail-actions { display: flex; align-items: center; gap: 6px; margin-top: 6px; }
.link-btn { border: 0; background: transparent; color: var(--c-primary); cursor: pointer; font-size: 12px; padding: 2px 4px; border-radius: 3px; }
.link-btn:hover { text-decoration: underline; }
.sep { color: var(--c-text-muted); font-size: 12px; }
.inline-approval { margin-top: 8px; padding: 14px; background: var(--c-surface); border: 1px solid var(--c-border); border-left: 3px solid var(--c-primary); border-radius: var(--radius); }
.inline-approval.dangerous { border-left-color: var(--c-danger); background: var(--c-danger-soft); }
.approval-head { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.title { font-weight: 700; font-size: 13px; }
.danger-tag { font-size: 10px; font-weight: 700; color: #fff; background: var(--c-danger); padding: 1px 6px; border-radius: 3px; }
.prompt { margin: 0 0 10px; font-size: 13px; line-height: 1.55; color: var(--c-text-secondary); }
.field { margin-bottom: 8px; }
.field label { display: block; font-size: 11px; color: var(--c-text-muted); margin-bottom: 3px; }
.field input, .field textarea { display: block; width: 100%; padding: 7px 10px; border: 1px solid var(--c-border); border-radius: var(--radius); font-size: 13px; outline: none; background: var(--c-surface); }
.actions { display: flex; gap: 8px; justify-content: flex-end; }
</style>
